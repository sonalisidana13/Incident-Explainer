import express from 'express';
import { pool } from './db/client.js';
import { runMigrations } from './db/migrate.js';

const app = express();
const port = Number(process.env.PORT || 8080);

app.use(express.json());

app.get('/health', async (_req, res) => {
  try {
    await pool.query('SELECT 1');
    res.status(200).json({
      status: 'ok',
      service: 'incident-explainer-api'
    });
  } catch (error) {
    res.status(503).json({
      status: 'error',
      message: 'database unavailable'
    });
  }
});

async function start() {
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL is required');
  }

  await runMigrations();

  app.listen(port, () => {
    console.log(`incident-explainer-api listening on port ${port}`);
  });
}

start().catch((error) => {
  console.error('failed to start API', error);
  process.exit(1);
});
