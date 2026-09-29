package com.esnexius.sitemanager;

public final class Operations {
    private Operations() {}

    private static final String COMMON = """
        set -eu
        TARGET="$HOME/esnexiusstore-phone-shop"
        ZIP="$HOME/storage/downloads/EsnexiusManager/upload.zip"
        MANAGER="$TARGET/.manager"
        backup_base() {
          if [ -d "$HOME/storage/downloads" ]; then
            printf '%s' "$HOME/storage/downloads/EsnexiusStoreBackups"
          else
            printf '%s' "$HOME/esnexiusstore-backups"
          fi
        }
        stop_server() {
          pidfile="$TARGET/.manager/server.pid"
          if [ -f "$pidfile" ]; then
            pid=$(cat "$pidfile" 2>/dev/null || true)
            [ -z "$pid" ] || kill "$pid" 2>/dev/null || true
            i=0
            while [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && [ "$i" -lt 10 ]; do
              sleep 1; i=$((i+1))
            done
            [ -z "$pid" ] || kill -9 "$pid" 2>/dev/null || true
            rm -f "$pidfile"
          fi
        }
        start_server() {
          [ -f "$TARGET/server.js" ] || return 1
          mkdir -p "$TARGET/.manager"
          pidfile="$TARGET/.manager/server.pid"
          logfile="$TARGET/.manager/server.log"
          if [ -f "$pidfile" ]; then
            pid=$(cat "$pidfile" 2>/dev/null || true)
            if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then return 0; fi
            rm -f "$pidfile"
          fi
          cd "$TARGET"
          nohup node server.js >>"$logfile" 2>&1 </dev/null &
          pid=$!
          echo "$pid" > "$pidfile"
          sleep 2
          kill -0 "$pid" 2>/dev/null
        }
        health_check() {
          i=0
          while [ "$i" -lt 8 ]; do
            if command -v curl >/dev/null 2>&1 && curl -fsS --max-time 2 http://127.0.0.1:3000/ >/dev/null 2>&1; then
              return 0
            fi
            sleep 1; i=$((i+1))
          done
          return 1
        }
        backup_site() {
          [ -d "$TARGET" ] || { echo "BACKUP=skipped:not-installed"; return 0; }
          base=$(backup_base); mkdir -p "$base"
          stamp=$(date +%Y%m%d-%H%M%S)
          out="$base/esnexiusstore-$stamp.tar.gz"
          tar -C "$TARGET" --exclude='./node_modules' --exclude='./.manager' -czf "$out" .
          echo "BACKUP=$out"
        }
        validate_zip() {
          [ -f "$ZIP" ] || { echo "No uploaded website ZIP. Tap UPLOAD ZIP first." >&2; exit 48; }
          bad=$(unzip -Z1 "$ZIP" | grep -E '(^/|(^|/)\.\.(/|$)|(^|/)\.git(/|$))' || true)
          [ -z "$bad" ] || { echo "Unsafe ZIP paths detected." >&2; exit 49; }
          entries=$(unzip -Z1 "$ZIP" | wc -l | tr -d ' ')
          [ "$entries" -le 5000 ] || { echo "ZIP contains too many files." >&2; exit 52; }
          total=$(unzip -l "$ZIP" | awk 'NR>3 && $1 ~ /^[0-9]+$/ {s+=$1} END {printf "%.0f", s+0}')
          [ "$total" -le 536870912 ] || { echo "ZIP expands beyond the 512 MB safety limit." >&2; exit 53; }
          if unzip -Z -v "$ZIP" 2>/dev/null | grep -E '(^|[[:space:]])l[rwx-]{9}([[:space:]]|$)' >/dev/null 2>&1; then
            echo "ZIP contains symbolic links, which are not allowed." >&2; exit 54
          fi
        }
        prepare_tree() {
          src="$1"; staged="$2"
          rm -rf "$staged"; mkdir -p "$staged"
          cp -R "$src"/. "$staged"/
          rm -rf "$staged/node_modules" "$staged/.manager"
          if [ -f "$TARGET/.env" ]; then cp "$TARGET/.env" "$staged/.env"; fi
          if [ -d "$TARGET/data" ]; then rm -rf "$staged/data"; cp -R "$TARGET/data" "$staged/data"; fi
          [ -f "$staged/server.js" ] || { echo "server.js is missing." >&2; return 1; }
          [ -f "$staged/package.json" ] || { echo "package.json is missing." >&2; return 1; }
          node --check "$staged/server.js" >/dev/null
          cd "$staged"
          if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi
          npm ls --omit=dev --depth=0 >/dev/null
          mkdir -p "$staged/.manager"
        }
        activate_tree() {
          staged="$1"
          old="$HOME/.esnexius-previous-$$"
          was_running=no
          pidfile="$TARGET/.manager/server.pid"
          if [ -f "$pidfile" ]; then
            oldpid=$(cat "$pidfile" 2>/dev/null || true)
            if [ -n "$oldpid" ] && kill -0 "$oldpid" 2>/dev/null; then was_running=yes; fi
          fi
          stop_server
          rm -rf "$old"
          if [ -d "$TARGET" ]; then mv "$TARGET" "$old"; fi
          mv "$staged" "$TARGET"
          if start_server && health_check; then
            rm -rf "$old"
            echo "HEALTH=online"
            echo "ROLLBACK=not-needed"
            return 0
          fi
          echo "New deployment failed health check; rolling back." >&2
          stop_server
          rm -rf "$TARGET"
          if [ -d "$old" ]; then mv "$old" "$TARGET"; fi
          if [ "$was_running" = yes ]; then start_server || true; fi
          echo "ROLLBACK=restored"
          return 70
        }
        """;

    public static String install() {
        return COMMON + """
            echo "STEP=1/4 Installing Termux packages"
            pkg update -y
            pkg install -y nodejs git unzip curl
            echo "STEP=2/4 Checking Node.js"
            node -e "const [M,m]=process.versions.node.split('.').map(Number);if(M<22||(M===22&&m<5))process.exit(2)" || { echo "Node.js 22.5+ required." >&2; exit 43; }
            echo "STEP=3/4 Dependencies ready"
            if [ -f "$ZIP" ]; then
              validate_zip
              tmp="$HOME/.esnexius-upload-$$"; rm -rf "$tmp"; mkdir -p "$tmp"
              trap 'rm -rf "$tmp"' EXIT
              unzip -q "$ZIP" -d "$tmp"
              server=$(find "$tmp" -maxdepth 3 -type f -name server.js -print -quit)
              [ -n "$server" ] || { echo "ZIP does not contain server.js." >&2; exit 50; }
              src=${server%/server.js}
              staged="$HOME/.esnexius-stage-$$"
              prepare_tree "$src" "$staged"
              backup_site
              activate_tree "$staged"
            fi
            echo "STEP=4/4 Complete"
            [ -d "$HOME/storage/downloads" ] && echo "STORAGE=ready" || echo "STORAGE=needs-termux-setup-storage"
            echo "INSTALL=ok"
            """;
    }

    public static String upload() { return deployCommand("UPLOAD"); }
    public static String update() { return deployCommand("UPDATE"); }

    private static String deployCommand(String label) {
        return COMMON + """
            command -v unzip >/dev/null || { echo "Run INSTALL first." >&2; exit 46; }
            command -v npm >/dev/null || { echo "Run INSTALL first." >&2; exit 47; }
            echo "STEP=1/6 Validating ZIP"
            validate_zip
            tmp="$HOME/.esnexius-upload-$$"; staged="$HOME/.esnexius-stage-$$"
            rm -rf "$tmp" "$staged"; mkdir -p "$tmp"
            trap 'rm -rf "$tmp" "$staged"' EXIT
            unzip -q "$ZIP" -d "$tmp"
            server=$(find "$tmp" -maxdepth 3 -type f -name server.js -print -quit)
            [ -n "$server" ] || { echo "ZIP does not contain server.js." >&2; exit 50; }
            src=${server%/server.js}
            [ -f "$src/package.json" ] || { echo "package.json is missing beside server.js." >&2; exit 51; }
            echo "STEP=2/6 Preparing staged deployment"
            prepare_tree "$src" "$staged"
            echo "STEP=3/6 Creating safety backup"
            backup_site
            echo "STEP=4/6 Switching deployment"
            activate_tree "$staged"
            echo "STEP=5/6 Verifying server"
            health_check
            echo "STEP=6/6 Complete"
            """ + "echo \"" + label + "=ok\"\n";
    }

    public static String backup() { return COMMON + "backup_site\necho BACKUP_ACTION=ok\n"; }

    public static String restart() {
        return COMMON + """
            [ -f "$TARGET/server.js" ] || { echo "Website not installed." >&2; exit 60; }
            stop_server
            start_server
            health_check
            echo RESTART=ok
            """;
    }

    public static String start() {
        return COMMON + """
            [ -f "$TARGET/server.js" ] || { echo "Website not installed. Tap UPLOAD ZIP, then INSTALL/UPDATE." >&2; exit 60; }
            start_server
            health_check
            pid=$(cat "$TARGET/.manager/server.pid" 2>/dev/null || true)
            echo START=ok
            echo "PID=$pid"
            echo "URL=http://127.0.0.1:3000"
            """;
    }

    public static String ensureRunning() {
        return COMMON + """
            [ -f "$TARGET/server.js" ] || { echo KEEPALIVE=not-installed; exit 0; }
            pidfile="$TARGET/.manager/server.pid"
            pid=""; [ -f "$pidfile" ] && pid=$(cat "$pidfile" 2>/dev/null || true)
            if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && health_check; then echo KEEPALIVE=healthy; exit 0; fi
            stop_server
            if start_server && health_check; then echo KEEPALIVE=restarted; else echo KEEPALIVE=failed >&2; exit 62; fi
            """;
    }

    public static String stop() { return COMMON + "stop_server\necho STOP=ok\n"; }

    public static String status() {
        return COMMON + """
            echo TERMUX=ok
            command -v node >/dev/null 2>&1 && echo "NODE=$(node -v)" || echo NODE=missing
            [ -f "$TARGET/server.js" ] && echo INSTALLED=yes || echo INSTALLED=no
            [ -d "$HOME/storage/downloads" ] && echo STORAGE=ready || echo STORAGE=not-ready
            [ -f "$TARGET/package-lock.json" ] && echo LOCKFILE=yes || echo LOCKFILE=no
            if [ -f "$TARGET/package.json" ] && command -v node >/dev/null 2>&1; then
              version=$(cd "$TARGET" && node -p "try{require('./package.json').version||'unknown'}catch(e){'unknown'}" 2>/dev/null || echo unknown)
              echo "VERSION=$version"
            else echo VERSION=unknown; fi
            pidfile="$TARGET/.manager/server.pid"; pid=""; [ -f "$pidfile" ] && pid=$(cat "$pidfile" 2>/dev/null || true)
            if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
              echo SERVER=running; echo "PID=$pid"
              uptime=$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ' || true)
              [ -n "$uptime" ] && echo "UPTIME=$uptime" || echo UPTIME=unknown
            else echo SERVER=stopped; fi
            command -v curl >/dev/null 2>&1 && curl -fsS --max-time 2 http://127.0.0.1:3000/ >/dev/null 2>&1 && echo HTTP=online || echo HTTP=offline
            base=$(backup_base); mkdir -p "$base"
            count=$(find "$base" -maxdepth 1 -type f -name 'esnexiusstore-*.tar.gz' | wc -l | tr -d ' ')
            echo "BACKUPS=$count"
            free=$(df -Pm "$HOME" 2>/dev/null | awk 'NR==2 {print $4}')
            [ -n "$free" ] && echo "FREE_MB=$free" || echo FREE_MB=unknown
            """;
    }

    public static String logs() {
        return "f=\"$HOME/esnexiusstore-phone-shop/.manager/server.log\"; [ -f \"$f\" ] && tail -n 200 \"$f\" || echo 'No server log yet.'";
    }

    public static String listBackups() {
        return COMMON + """
            base=$(backup_base); mkdir -p "$base"
            found=no
            for f in "$base"/esnexiusstore-*.tar.gz; do
              [ -f "$f" ] || continue
              found=yes
              name=$(basename "$f")
              size=$(du -h "$f" | awk '{print $1}')
              echo "BACKUP_ITEM=$name|$size"
            done
            [ "$found" = yes ] || echo BACKUP_EMPTY=yes
            """;
    }

    public static String restore(String filename) {
        String q = shellQuote(filename);
        return COMMON + """
            name=""" + q + """
            case "$name" in esnexiusstore-[0-9]*.tar.gz) ;; *) echo "Invalid backup name." >&2; exit 81;; esac
            base=$(backup_base); archive="$base/$name"
            [ -f "$archive" ] || { echo "Backup not found." >&2; exit 82; }
            echo "STEP=1/5 Backing up current site"
            backup_site
            staged="$HOME/.esnexius-restore-$$"; rm -rf "$staged"; mkdir -p "$staged"
            trap 'rm -rf "$staged"' EXIT
            echo "STEP=2/5 Extracting backup"
            tar -xzf "$archive" -C "$staged"
            echo "STEP=3/5 Installing dependencies"
            [ -f "$staged/server.js" ] || { echo "Backup has no server.js." >&2; exit 83; }
            [ -f "$staged/package.json" ] || { echo "Backup has no package.json." >&2; exit 84; }
            node --check "$staged/server.js" >/dev/null
            cd "$staged"
            if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi
            npm ls --omit=dev --depth=0 >/dev/null
            mkdir -p "$staged/.manager"
            echo "STEP=4/5 Activating backup"
            activate_tree "$staged"
            echo "STEP=5/5 Complete"
            echo "RESTORE=$name"
            """;
    }

    public static String deleteBackup(String filename) {
        String q = shellQuote(filename);
        return COMMON + """
            name=""" + q + """
            case "$name" in esnexiusstore-[0-9]*.tar.gz) ;; *) echo "Invalid backup name." >&2; exit 81;; esac
            base=$(backup_base); archive="$base/$name"
            [ -f "$archive" ] || { echo "Backup not found." >&2; exit 82; }
            rm -f "$archive"
            echo "DELETE_BACKUP=$name"
            """;
    }

    public static String termuxSetupCommand() {
        return "mkdir -p ~/.termux; if grep -q '^allow-external-apps=' ~/.termux/termux.properties 2>/dev/null; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else echo 'allow-external-apps=true' >> ~/.termux/termux.properties; fi; termux-reload-settings; termux-setup-storage";
    }

    private static String shellQuote(String value) {
        if (value == null) value = "";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
