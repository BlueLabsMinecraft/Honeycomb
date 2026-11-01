# Display smoke test

These scripts join a running test server with one or two headless bots and read
the packets the client receives, so a display can be verified without a human
watching the screen.

Requirements: Node.js 20+, `npm i mineflayer`, a Paper server with
`online-mode=false`, and the Honeycomb jar in `plugins/`.

```bash
node probe2.js   # sidebar packets, tablist, two players, nametag teams
node probe3.js   # nametag team packets only
```

Notes:

* mineflayer's bundled protocol support currently stops at Minecraft 1.21.4, so
  these scripts run against a 1.21.4 server. The display code is identical; the
  newer artifacts were verified for loading and commands only.
* Scoreboard fields in the raw packets are `display_name` and `number_format`,
  not the camelCase names.
* The clientbound team packet is named `teams`.
