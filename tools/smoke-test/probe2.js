const mineflayer = require('mineflayer');
const mk = (name) => mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.4', auth: 'offline' });
const a = mk('Tester');
const b = mk('Guest');
const seen = { scores: [], teams: [] };
b.on('error', e => { console.log('ERR ' + e.message); process.exit(1); });
b._client.on('packet', (data, meta) => {
  if (meta.name === 'scoreboard_score' && seen.scores.length < 3) seen.scores.push(JSON.parse(JSON.stringify(data)));
  if (meta.name === 'scoreboard_team') seen.teams.push({ team: data.team, mode: data.mode, prefix: String(data.prefix), suffix: String(data.suffix), players: data.players, nameTagVisibility: data.nameTagVisibility });
});
let ready = 0;
const onSpawn = async () => {
  if (++ready < 2) return;
  await a.waitForTicks(200);
  const items = [];
  for (const board of Object.values(b.scoreboards || {})) {
    for (const item of board.items || []) items.push({ v: item.value, name: String(item.name), display: item.displayName ? String(item.displayName) : null });
  }
  console.log('RESULT ' + JSON.stringify({
    rawScores: seen.scores,
    teams: seen.teams.slice(-6),
    teamCount: seen.teams.length,
    sidebarItems: items.slice(0, 10),
    players: Object.keys(b.players)
  }));
  a.quit(); b.quit(); setTimeout(() => process.exit(0), 400);
};
a.once('spawn', onSpawn); b.once('spawn', onSpawn);
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 90000);
