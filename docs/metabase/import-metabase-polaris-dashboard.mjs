#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";

const templatePath = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.resolve(process.cwd(), "METABASE_POLARIS_DASHBOARD_IMPORT.json");

const metabaseUrl = (process.env.METABASE_URL || "http://localhost:3000").replace(/\/+$/, "");
const forceNewCollection = process.env.METABASE_FORCE_NEW_COLLECTION !== "false";

function readSql(sql) {
  return Array.isArray(sql) ? sql.join("\n") : sql;
}

function jsonHeaders(extra = {}) {
  return {
    "Content-Type": "application/json",
    ...extra
  };
}

async function parseJsonResponse(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function main() {
  const template = JSON.parse(await fs.readFile(templatePath, "utf8"));

  console.log("📊 Polaris Metabase dashboard import를 시작합니다.");
  console.log(`- Template: ${templatePath}`);
  console.log(`- Metabase: ${metabaseUrl}`);

  let authHeader = {};

  if (process.env.METABASE_API_KEY) {
    authHeader = { "X-API-Key": process.env.METABASE_API_KEY };
    console.log("🔐 METABASE_API_KEY로 인증합니다.");
  } else {
    const email = process.env.METABASE_EMAIL;
    const password = process.env.METABASE_PASSWORD;

    if (!email || !password) {
      throw new Error(
        "METABASE_API_KEY 또는 METABASE_EMAIL/METABASE_PASSWORD가 필요합니다.\n" +
          "예: METABASE_URL=http://localhost:3000 METABASE_EMAIL=admin@example.com METABASE_PASSWORD='password' node import-metabase-polaris-dashboard.mjs"
      );
    }

    const loginResponse = await fetch(`${metabaseUrl}/api/session`, {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify({ username: email, password })
    });

    const loginBody = await parseJsonResponse(loginResponse);

    if (!loginResponse.ok) {
      throw new Error(`Metabase login 실패: ${loginResponse.status} ${JSON.stringify(loginBody)}`);
    }

    authHeader = { "X-Metabase-Session": loginBody.id };
    console.log("🔐 email/password session으로 인증했습니다.");
  }

  async function api(endpoint, options = {}) {
    const response = await fetch(`${metabaseUrl}${endpoint}`, {
      ...options,
      headers: {
        ...jsonHeaders(),
        ...authHeader,
        ...(options.headers || {})
      }
    });

    const body = await parseJsonResponse(response);

    if (!response.ok) {
      const error = new Error(`${options.method || "GET"} ${endpoint} 실패: ${response.status} ${JSON.stringify(body)}`);
      error.status = response.status;
      error.body = body;
      throw error;
    }

    return body;
  }

  const databaseIds = await resolveDatabaseIds(api, template.databaseKeys);
  const collection = await createCollection(api, template.collection);
  const cardIds = await createCards(api, template.cards, databaseIds, collection.id);
  const dashboards = await createDashboards(api, template.dashboards, template.cards, cardIds, collection.id);

  console.log("");
  console.log("✅ Import 완료");
  console.log(`- Collection: ${collection.name} (#${collection.id})`);
  for (const dashboard of dashboards) {
    console.log(`- Dashboard: ${dashboard.name} -> ${metabaseUrl}/dashboard/${dashboard.id}`);
  }
  console.log("");
  console.log("다음 단계:");
  console.log("1. Metabase에서 카드별 visualization이 원하는 모양인지 한 번 확인합니다.");
  console.log("2. 날짜 filter가 필요하면 UI에서 추가하고 주요 카드에 연결합니다.");
  console.log("3. 포트폴리오 캡처는 Executive Overview 첫 화면을 추천합니다.");
}

async function resolveDatabaseIds(api, databaseKeys) {
  const databaseListResponse = await api("/api/database");
  const databases = Array.isArray(databaseListResponse)
    ? databaseListResponse
    : databaseListResponse.data || [];

  const resolved = {};

  for (const [key, config] of Object.entries(databaseKeys)) {
    const envValue = process.env[config.env];
    if (envValue) {
      resolved[key] = Number(envValue);
      console.log(`🧩 DB ${key}: env ${config.env}=${resolved[key]} 사용`);
      continue;
    }

    const preferredNames = (config.preferredNames || []).map((name) => name.toLowerCase());
    const matched = databases.find((database) =>
      preferredNames.includes(String(database.name || "").toLowerCase())
    );

    if (!matched) {
      const known = databases.map((database) => `#${database.id} ${database.name}`).join(", ");
      throw new Error(
        `DB '${key}'를 자동으로 찾지 못했습니다.\n` +
          `Metabase DB 이름 후보: ${config.preferredNames.join(", ")}\n` +
          `현재 Metabase DB 목록: ${known || "(비어 있음)"}\n` +
          `해결: ${config.env}=숫자ID 를 환경변수로 넣고 다시 실행하세요.`
      );
    }

    resolved[key] = matched.id;
    console.log(`🧩 DB ${key}: #${matched.id} ${matched.name} 자동 매칭`);
  }

  return resolved;
}

async function createCollection(api, collectionConfig) {
  const suffix = new Date().toISOString().replace("T", " ").slice(0, 16);
  const name = forceNewCollection
    ? `${collectionConfig.name} (${suffix})`
    : collectionConfig.name;

  if (!forceNewCollection) {
    const existingResponse = await api("/api/collection");
    const collections = Array.isArray(existingResponse)
      ? existingResponse
      : existingResponse.data || [];
    const existing = collections.find((collection) => collection.name === name);
    if (existing) {
      console.log(`📁 기존 collection 사용: #${existing.id} ${existing.name}`);
      return existing;
    }
  }

  const collection = await api("/api/collection", {
    method: "POST",
    body: JSON.stringify({
      name,
      description: collectionConfig.description,
      color: collectionConfig.color || "#509EE3",
      parent_id: null
    })
  });

  console.log(`📁 collection 생성: #${collection.id} ${collection.name}`);
  return collection;
}

async function createCards(api, cards, databaseIds, collectionId) {
  const cardIds = {};

  for (const card of cards) {
    const databaseId = databaseIds[card.databaseKey];

    if (!databaseId) {
      throw new Error(`card '${card.key}'의 databaseKey '${card.databaseKey}'를 찾지 못했습니다.`);
    }

    const payload = {
      name: card.name,
      description: card.description,
      display: card.display,
      type: "question",
      collection_id: collectionId,
      dataset_query: {
        database: databaseId,
        type: "native",
        native: {
          query: readSql(card.sql),
          "template-tags": {}
        }
      },
      visualization_settings: card.visualizationSettings || {},
      parameters: [],
      parameter_mappings: []
    };

    const created = await api("/api/card", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    cardIds[card.key] = created.id;
    console.log(`🃏 card 생성: #${created.id} ${card.name}`);
  }

  return cardIds;
}

async function createDashboards(api, dashboards, cards, cardIds, collectionId) {
  const createdDashboards = [];
  const cardByKey = new Map(cards.map((card) => [card.key, card]));

  for (const dashboardConfig of dashboards) {
    const dashboard = await api("/api/dashboard", {
      method: "POST",
      body: JSON.stringify({
        name: dashboardConfig.name,
        description: dashboardConfig.description,
        collection_id: collectionId,
        parameters: []
      })
    });

    console.log(`🖼️ dashboard 생성: #${dashboard.id} ${dashboard.name}`);

    const dashcards = dashboardConfig.cards.map((layout, index) => {
      if (layout.type === "text") {
        return {
          id: -index - 1,
          card_id: null,
          row: layout.row,
          col: layout.col,
          size_x: layout.sizeX,
          size_y: layout.sizeY,
          dashboard_tab_id: null,
          visualization_settings: {
            virtual_card: {
              name: null,
              display: "text",
              visualization_settings: {},
              dataset_query: {},
              archived: false
            },
            text: layout.text,
            "dashcard.background": layout.background ?? false,
            "text.align_horizontal": layout.alignHorizontal || "left",
            "text.align_vertical": layout.alignVertical || "middle"
          },
          parameter_mappings: [],
          series: []
        };
      }

      const sourceCard = cardByKey.get(layout.cardKey);

      return {
        id: -index - 1,
        card_id: cardIds[layout.cardKey],
        row: layout.row,
        col: layout.col,
        size_x: layout.sizeX,
        size_y: layout.sizeY,
        dashboard_tab_id: null,
        visualization_settings: {
          "card.title": layout.title || sourceCard?.name || layout.cardKey,
          ...(layout.visualizationSettings || {})
        },
        parameter_mappings: [],
        series: []
      };
    });

    await attachCardsToDashboard(api, dashboard.id, dashcards);

    createdDashboards.push(dashboard);
  }

  return createdDashboards;
}

async function attachCardsToDashboard(api, dashboardId, dashcards) {
  try {
    await api(`/api/dashboard/${dashboardId}/cards`, {
      method: "PUT",
      body: JSON.stringify({ cards: dashcards })
    });
    console.log(`🧱 dashboard #${dashboardId}에 ${dashcards.length}개 card layout 적용: PUT /cards`);
    return;
  } catch (putCardsError) {
    console.log(`ℹ️ PUT /api/dashboard/${dashboardId}/cards 실패. 현재 버전용 PUT /api/dashboard/${dashboardId} fallback을 시도합니다.`);
  }

  try {
    const current = await api(`/api/dashboard/${dashboardId}`);
    await api(`/api/dashboard/${dashboardId}`, {
      method: "PUT",
      body: JSON.stringify({
        name: current.name,
        description: current.description,
        collection_id: current.collection_id,
        parameters: current.parameters || [],
        tabs: current.tabs || [],
        dashcards
      })
    });
    console.log(`🧱 dashboard #${dashboardId}에 ${dashcards.length}개 card layout 적용: PUT /dashboard`);
    return;
  } catch (putDashboardError) {
    console.log(`ℹ️ PUT /api/dashboard/${dashboardId} fallback 실패. 구버전 POST /cards 방식을 시도합니다.`);
  }

  for (const dashcard of dashcards) {
    await api(`/api/dashboard/${dashboardId}/cards`, {
      method: "POST",
      body: JSON.stringify({
        cardId: dashcard.card_id,
        row: dashcard.row,
        col: dashcard.col,
        size_x: dashcard.size_x,
        size_y: dashcard.size_y,
        visualization_settings: dashcard.visualization_settings,
        parameter_mappings: []
      })
    });
  }

  console.log(`🧱 dashboard #${dashboardId}에 ${dashcards.length}개 card layout 적용: POST /cards`);
}

main().catch((error) => {
  console.error("");
  console.error("❌ Import 실패");
  console.error(error.message);
  if (error.body) {
    console.error(JSON.stringify(error.body, null, 2));
  }
  process.exitCode = 1;
});
