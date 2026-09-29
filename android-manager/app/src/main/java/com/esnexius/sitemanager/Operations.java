package com.esnexius.sitemanager;

public final class Operations {
    private Operations() {}

    private static final String PREFIX = """
        set -eu
        TARGET="$HOME/esnexiusstore-phone-shop"
        ZIP="$HOME/storage/downloads/EsnexiusManager/upload.zip"
        backup_site() {
          [ -d "$TARGET" ] || { echo "BACKUP=skipped:not-installed"; return 0; }
          stamp=$(date +%Y%m%d-%H%M%S)
          if [ -d "$HOME/storage/downloads" ]; then
            base="$HOME/storage/downloads/EsnexiusStoreBackups"
          else
            base="$HOME/esnexiusstore-backups"
          fi
          mkdir -p "$base"
          out="$base/esnexiusstore-$stamp.tar.gz"
          tar -C "$TARGET" --exclude='./node_modules' --exclude='./.manager' -czf "$out" .
          echo "BACKUP=$out"
        }
        deploy_zip() {
          [ -f "$ZIP" ] || { echo "No uploaded website ZIP. Tap UPLOAD ZIP first." >&2; exit 48; }
          bad=$(unzip -Z1 "$ZIP" | grep -E '(^/|(^|/)\\.\\.(/|$))' || true)
          [ -z "$bad" ] || { echo "Unsafe ZIP paths detected." >&2; exit 49; }
          tmp="$HOME/.esnexius-upload-$$"
          rm -rf "$tmp"; mkdir -p "$tmp"
          trap 'rm -rf "$tmp"' EXIT
          unzip -q "$ZIP" -d "$tmp"
          server=$(find "$tmp" -maxdepth 3 -type f -name server.js -print -quit)
          [ -n "$server" ] || { echo "ZIP does not contain server.js." >&2; exit 50; }
          src=${server%/server.js}
          [ -f "$src/package.json" ] || { echo "package.json is missing beside server.js." >&2; exit 51; }

          mkdir -p "$TARGET"
          [ -f "$TARGET/.env" ] && cp "$TARGET/.env" "$tmp/.saved-env"
          [ -d "$TARGET/data" ] && cp -R "$TARGET/data" "$tmp/.saved-data"

          find "$TARGET" -mindepth 1 -maxdepth 1 ! -name '.env' ! -name 'data' ! -name '.manager' -exec rm -rf {} +
          cp -R "$src"/. "$TARGET"/
          rm -rf "$TARGET/node_modules"
          [ -f "$tmp/.saved-env" ] && cp "$tmp/.saved-env" "$TARGET/.env"
          [ -d "$tmp/.saved-data" ] && { rm -rf "$TARGET/data"; cp -R "$tmp/.saved-data" "$TARGET/data"; }
          if [ ! -f "$TARGET/.env" ] && [ -f "$TARGET/.env.example" ]; then cp "$TARGET/.env.example" "$TARGET/.env"; echo "ENV=created"; else echo "ENV=preserved"; fi
          cd "$TARGET"
          npm install --omit=dev
          mkdir -p "$TARGET/.manager"
          echo "DEPLOY=ok"
        }
        """;

    public static String install() {
        return PREFIX + """
            echo "STEP=Installing Termux packages"
            pkg update -y
            pkg install -y nodejs git unzip curl
            node -e "const [M,m]=process.versions.node.split('.').map(Number);if(M<22||(M===22&&m<5))process.exit(2)" || { echo "Node.js 22.5+ required." >&2; exit 43; }
            echo "INSTALL=dependencies-ready"
            if [ -f "$ZIP" ]; then deploy_zip; fi
            if [ -d "$HOME/storage/downloads" ]; then echo "STORAGE=ready"; else echo "STORAGE=needs-termux-setup-storage"; fi
            """;
    }

    public static String upload() {
        return PREFIX + """
            command -v unzip >/dev/null || { echo "Run INSTALL first." >&2; exit 46; }
            command -v npm >/dev/null || { echo "Run INSTALL first." >&2; exit 47; }
            backup_site
            deploy_zip
            echo "UPLOAD=ok"
            """;
    }

    public static String update() {
        return PREFIX + """
            command -v unzip >/dev/null || { echo "Run INSTALL first." >&2; exit 46; }
            command -v npm >/dev/null || { echo "Run INSTALL first." >&2; exit 47; }
            backup_site
            deploy_zip
            echo "UPDATE=ok"
            """;
    }

    public static String backup() {
        return PREFIX + "backup_site\necho BACKUP_ACTION=ok\n";
    }

    public static String start() {
        return """
            set -eu
            TARGET="$HOME/esnexiusstore-phone-shop"
            [ -f "$TARGET/server.js" ] || { echo "Website not installed. Tap UPLOAD ZIP, then INSTALL/UPDATE." >&2; exit 60; }
            mkdir -p "$TARGET/.manager"
            PIDFILE="$TARGET/.manager/server.pid"; LOGFILE="$TARGET/.manager/server.log"
            if [ -f "$PIDFILE" ]; then
              pid=$(cat "$PIDFILE" 2>/dev/null || true)
              if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then echo "START=already-running"; echo "PID=$pid"; exit 0; fi
              rm -f "$PIDFILE"
            fi
            cd "$TARGET"
            nohup node server.js >"$LOGFILE" 2>&1 </dev/null &
            pid=$!; echo "$pid" > "$PIDFILE"; sleep 2
            if kill -0 "$pid" 2>/dev/null; then echo "START=ok"; echo "PID=$pid"; echo "URL=http://127.0.0.1:3000"; else tail -n 50 "$LOGFILE" >&2 || true; rm -f "$PIDFILE"; exit 62; fi
            """;
    }

    public static String stop() {
        return """
            TARGET="$HOME/esnexiusstore-phone-shop"; PIDFILE="$TARGET/.manager/server.pid"
            if [ -f "$PIDFILE" ]; then pid=$(cat "$PIDFILE" 2>/dev/null); [ -n "$pid" ] && kill "$pid" 2>/dev/null || true; rm -f "$PIDFILE"; fi
            echo STOP=ok
            """;
    }

    public static String status() {
        return """
            TARGET="$HOME/esnexiusstore-phone-shop"
            echo TERMUX=ok
            command -v node >/dev/null 2>&1 && echo "NODE=$(node -v)" || echo NODE=missing
            [ -f "$TARGET/server.js" ] && echo INSTALLED=yes || echo INSTALLED=no
            [ -d "$HOME/storage/downloads" ] && echo STORAGE=ready || echo STORAGE=not-ready
            PIDFILE="$TARGET/.manager/server.pid"; pid=""; [ -f "$PIDFILE" ] && pid=$(cat "$PIDFILE" 2>/dev/null)
            if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then echo SERVER=running; echo "PID=$pid"; else echo SERVER=stopped; fi
            command -v curl >/dev/null 2>&1 && curl -fsS --max-time 2 http://127.0.0.1:3000/ >/dev/null 2>&1 && echo HTTP=online || echo HTTP=offline
            """;
    }

    public static String logs() {
        return "f=\"$HOME/esnexiusstore-phone-shop/.manager/server.log\"; [ -f \"$f\" ] && tail -n 100 \"$f\" || echo 'No server log yet.'";
    }

    public static String termuxSetupCommand() {
        return "mkdir -p ~/.termux; if grep -q '^allow-external-apps=' ~/.termux/termux.properties 2>/dev/null; then sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties; else echo 'allow-external-apps=true' >> ~/.termux/termux.properties; fi; termux-reload-settings; termux-setup-storage";
    }
}
