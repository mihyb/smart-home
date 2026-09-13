> **Historical.** This describes the August 2026 move of HomeController off
> `192.168.1.109` onto `192.168.1.124`. Both of those hosts have since been
> retired: HomeController and openHAB now run together on `192.168.1.132`. Kept
> for the provisioning steps, which still apply to a fresh host. See CLAUDE.md
> for the current topology and `scripts/cutover-controller.sh` for the move that
> superseded this one.

# Migrating HomeController off 192.168.1.109

`192.168.1.109` has only **732 MB of RAM**, and OpenHAB alone occupies ~448 MB of it.
There was never headroom for a second JVM, so HomeController moved to
`192.168.1.124` (user `majkl`, Ubuntu 24.04, 15 GiB RAM). OpenHAB **stays** on `.109`.

Status: migration completed 2026-08-22. `.124` is live, `enabled` for boot, health `UP`.

Because OpenHAB stays put, `app.openhab.baseUrl` still points at `.109:8080` — it is
now a LAN call instead of a loopback one. The host is no longer baked into the jar;
it comes from `OPENHAB_HOST` / `OPENHAB_PORT` (defaults preserved in `application.yaml`).

## Prerequisites on 192.168.1.124

- Reachable on the LAN (it was powered off / unassigned at the time of writing —
  `ping` failed and ARP was incomplete).
- A JRE 11 or newer: `sudo apt-get install -y openjdk-17-jre-headless`
- systemd, and `majkl` able to `sudo`.

## 1. Set up key-based SSH (once, from the Mac)

`deploy.sh` runs `scp` and `ssh` non-interactively, so password auth will not do:

```sh
ssh-copy-id majkl@192.168.1.124
ssh majkl@192.168.1.124 true   # must succeed without prompting
```

## 2. Provision the host (once)

```sh
scp deploy/provision.sh deploy/homecontroller.service majkl@192.168.1.124:/tmp/
ssh -t majkl@192.168.1.124 'bash /tmp/provision.sh'
```

This creates `/home/majkl/app`, installs and enables `homecontroller.service`, and
adds a sudoers rule letting `majkl` start/stop/restart *only* that unit without a
password.

## 3. Build and deploy

```sh
./build.sh     # needs JDK 17 (sdkman 17.0.7-tem)
./deploy.sh    # defaults to majkl@192.168.1.124
```

Override the target without editing the script: `HOST=192.168.1.130 ./deploy.sh`

## 4. Verify

```sh
ssh majkl@192.168.1.124 'systemctl is-active homecontroller'
ssh majkl@192.168.1.124 'journalctl -u homecontroller -n 100 --no-pager'
curl -s http://192.168.1.124:8181/actuator/health
```

Confirm the app is reaching OpenHAB across the LAN — a `TimerJobRunner` tick fires
every 5 minutes and logs at DEBUG under `com.hyblerm.homecontroller`.

## 5. Decommission on the old host

The old app process is already dead (it did not survive the memory pressure), so
nothing needs killing. **But `/etc/rc.local` on `.109` still autostarts it:**

```sh
sh /home/dev/app/run-home-portal.sh
```

That line must go, or the next reboot of `.109` brings back a second instance and
both hosts command the same switches every 5 minutes:

```sh
ssh dev@192.168.1.109 "sudo sed -i 's|^sh /home/dev/app/run-home-portal.sh|# migrated to 192.168.1.124 on 2026-08-22\n# &|' /etc/rc.local"
ssh dev@192.168.1.109 'grep -n run-home-portal /etc/rc.local'
```

Leave OpenHAB itself running — `.124` depends on it.

## Notes

- **Memory.** The unit caps the JVM at `-Xmx256m` with `MemoryMax=512M`, so this
  service cannot repeat what happened to `.109`. Tune in the unit file if the app
  turns out to need more.
- **H2** moved from `/tmp/data/demo` to `/home/majkl/app/data/demo` (via
  `H2_DATA_PATH`). It is only a scrape cache for electricity rates and is recreated
  on every boot (`ddl-auto: create`) — nothing to migrate.
- **Spring Boot Admin** self-registers at `http://127.0.0.1:8181`, so it follows the
  app automatically.
- **`run-home-portal.sh`** never lived in this repo. Recovered from `.109` for the
  record — it did nothing the systemd unit does not:

  ```sh
  sudo pkill -9 -f HomeController
  rm -f /home/dev/app/nohup.out
  nohup java -jar /home/dev/app/HomeController-0.0.1-SNAPSHOT.jar --app.port=8181 > /dev/null 2>&1&
  ```

- **Java 21.** `.124` ships OpenJDK 21, past Spring Boot 2.6's supported ceiling of 17.
  Smoke-tested before cutover and it starts clean, but if odd CGLIB/reflection errors
  ever appear, install `openjdk-17-jre-headless` and pin `ExecStart` to it.

- **Credentials.** `.claude/deploy` holds a password that no longer works; deploys use
  the SSH key. That file is excluded via `.git/info/exclude`.
