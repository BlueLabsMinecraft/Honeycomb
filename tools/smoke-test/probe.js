const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.4', auth: 'offline' });
const seen = { objectives: [], scores: [], bossbars: [], teams: [], header: null, footer: null };
bot.on('error', e => { console.log('ERR ' + e.message); process.exit(1); });
bot._client.on('packet', (data, meta) => {
  const n = meta.name;
  if (n === 'scoreboard_objective') seen.objectives.push({ name: data.name, display: String(data.displayText), mode: data.mode, slot: data.displaySlot });
  if (n === 'scoreboard_score') seen.scores.push({ entity: data.entityName, value: data.value, display: data.displayName ? String(data.displayName) : null, numberFormat: data.numberFormat ? JSON.stringify(data.numberFormat).slice(0, 90) : null });
  if (n === 'scoreboard_display_objective') seen.objectives.push({ slotSet: data.position, name: data.name });
  if (n === 'boss_bar') seen.bossbars.push({ action: data.action, title: data.title ? String(data.title) : null, health: data.health, color: data.color, division: data.division });
  if (n === 'scoreboard_team') seen.teams.push({ name: data.team, mode: data.mode, prefix: data.prefix ? String(data.prefix) : null, suffix: data.suffix ? String(data.suffix) : null, players: data.players });
});
bot.once('spawn', async () => {
  await bot.waitForTicks(200);
  const cap = (arr, n) => arr.slice(-n);
  console.log('RESULT ' + JSON.stringify({
    objectives: cap(seen.objectives, 6),
    scores: cap(seen.scores, 10),
    scoreCount: seen.scores.length,
    bossbars: cap(seen.bossbars, 4),
    teams: cap(seen.teams, 6),
    teamCount: seen.teams.length
  }));
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 70000);
