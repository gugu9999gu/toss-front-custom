"""Real TLS, pairing isolation and USB/LAN retry behavior without PC actions."""
from http.client import HTTPConnection, HTTPSConnection
import json
from pathlib import Path
import shutil
import ssl
import subprocess
import tempfile
import threading
import unittest
from urllib.parse import urlencode

from frontdeck.server import Controller, FrontDeckServer, load_config
from frontdeck.wireless import ensure_identity, lan_address

ROOT = Path(__file__).resolve().parents[1]


class Recorder:
    dry_run = True
    def __init__(self): self.ids = []
    def execute(self, action):
        self.ids.append(action['id'])
        return {'ok': True}


class IdentityTests(unittest.TestCase):
    def test_only_explicit_private_ipv4_is_accepted(self):
        for value in ('127.0.0.1', '0.0.0.0', '8.8.8.8', 'localhost', '::1', '192.168.001.10', '172.32.0.1'):
            with self.subTest(value=value), self.assertRaises(ValueError): lan_address(value)
        self.assertEqual(lan_address('192.168.1.10'), '192.168.1.10')

    def test_certificate_identity_survives_restart_and_address_change(self):
        with tempfile.TemporaryDirectory() as folder:
            first = ensure_identity(folder, '192.168.1.10')[1]
            self.assertEqual(ensure_identity(folder, '192.168.1.20')[1], first)
            Path(folder, 'wireless-key.pem').unlink()
            with self.assertRaises(ValueError): ensure_identity(folder, '192.168.1.10')
            self.assertTrue(Path(folder, 'wireless-cert.pem').exists())


class WirelessTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        context, self.fingerprint = ensure_identity(self.folder.name, '192.168.1.10')
        self.executor = Recorder()
        self.controller = Controller(load_config(ROOT / 'frontdeck/config.example.json'), 'fixture_token_' * 4, '12345678', self.executor)
        self.usb = FrontDeckServer(('127.0.0.1', 0), self.controller)
        self.lan = FrontDeckServer(('127.0.0.1', 0), self.controller, context)
        self.controller.wireless = {'host': '192.168.1.10', 'port': self.lan.server_port, 'fingerprint': self.fingerprint}
        self.controller.bootstrap_path = Path(self.folder.name, 'bootstrap.json')
        self.controller.bootstrap_path.write_text(json.dumps({'pin': self.controller.pin, 'wireless': self.controller.wireless}), encoding='utf-8')
        self.context = ssl.create_default_context(cafile=str(Path(self.folder.name, 'wireless-cert.pem')))
        self.threads = [threading.Thread(target=s.serve_forever, daemon=True) for s in (self.usb, self.lan)]
        for thread in self.threads: thread.start()

    def tearDown(self):
        for server in (self.usb, self.lan): server.shutdown(); server.server_close()
        for thread in self.threads: thread.join()
        self.folder.cleanup()

    def call(self, route, body=None, authenticated=False, headers=None, wireless=True, form=False):
        server = self.lan if wireless else self.usb
        connection = HTTPSConnection('127.0.0.1', server.server_port, context=self.context, timeout=3) if wireless else HTTPConnection('127.0.0.1', server.server_port, timeout=3)
        req_headers = {'Content-Type': 'application/x-www-form-urlencoded' if form else 'application/json'}
        if authenticated: req_headers['Authorization'] = 'Bearer ' + self.controller.token
        req_headers.update(headers or {})
        encoded = None if body is None else urlencode(body) if form else json.dumps(body)
        try:
            connection.request('GET' if body is None else 'POST', route, encoded, req_headers)
            response = connection.getresponse()
            return response.status, response.read()
        finally: connection.close()

    def test_real_tls_pairing_and_native_authorization(self):
        self.assertEqual(self.call('/api/config')[0], 401)
        self.assertEqual(self.call('/api/pair', {'pin': '87654321'})[0], 403)
        status, raw = self.call('/api/pair', {'pin': '12345678'})
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(raw)['token'], self.controller.token)
        self.assertEqual(self.call('/api/config', authenticated=True)[0], 200)
        body = {'id': 'notepad', 'request_id': 'wireless_origin_test'}
        self.assertEqual(self.call('/api/action', body, True, {'Origin': 'https://untrusted.example'})[0], 403)
        self.assertEqual(self.call('/api/action', body, True, {'Host': 'untrusted.example'})[0], 403)
        self.assertEqual(self.executor.ids, [])

    def test_retry_across_usb_and_wifi_executes_only_once(self):
        body = {'id': 'notepad', 'request_id': 'same_request_across_transports'}
        self.assertEqual(self.call('/api/action', body, True)[0], 200)
        self.assertEqual(self.call('/api/action', body, True, wireless=False)[0], 200)
        self.assertEqual(self.executor.ids, ['notepad'])
        self.assertEqual(self.call('/api/action', {**body, 'id': 'calculator'}, True, wireless=False)[0], 409)

    def test_setup_secrets_and_preview_are_local_only(self):
        for route in ('/connect/', '/preview/', '/preview/demo.json'):
            self.assertEqual(self.call(route)[0], 404)
        health = json.loads(self.call('/api/health')[1])
        for secret in ('wireless', 'pin', 'token', 'wireless_last_peer'):
            self.assertNotIn(secret, health)
        local_health = json.loads(self.call('/api/health', wireless=False)[1])
        self.assertEqual(local_health['wireless'], self.controller.wireless)
        status, raw = self.call('/connect/', wireless=False)
        self.assertEqual(status, 200)
        self.assertIn(b'12345678', raw)
        self.assertNotIn(self.controller.token.encode(), raw)

    def test_local_code_renewal_keeps_existing_clients_and_rejects_csrf(self):
        token, old_pin = self.controller.token, self.controller.pin
        body = {'nonce': self.controller.setup_nonce}
        self.assertEqual(self.call('/connect/renew', body, form=True)[0], 403)
        self.assertEqual(self.call('/connect/renew', {'nonce': 'wrong'}, wireless=False, form=True)[0], 403)
        self.assertEqual(self.call('/connect/renew', body, headers={'Origin': 'https://untrusted.example'}, wireless=False, form=True)[0], 403)
        self.assertEqual(self.call('/connect/renew', body, wireless=False, form=True)[0], 303)
        self.assertEqual(self.controller.token, token)
        saved = json.loads(self.controller.bootstrap_path.read_text(encoding='utf-8'))
        self.assertEqual(saved['pin'], self.controller.pin)
        self.assertEqual(saved['wireless'], self.controller.wireless)
        self.assertEqual(self.call('/api/config', authenticated=True)[0], 200)
        if self.controller.pin != old_pin:
            self.assertEqual(self.call('/api/pair', {'pin': old_pin})[0], 403)
        self.assertEqual(self.call('/api/pair', {'pin': saved['pin']})[0], 200)

    def test_cleartext_and_untrusted_certificates_cannot_execute(self):
        connection = HTTPSConnection('127.0.0.1', self.lan.server_port, context=ssl.create_default_context(), timeout=3)
        try:
            with self.assertRaises(ssl.SSLCertVerificationError): connection.request('GET', '/api/health')
        finally: connection.close()
        connection = HTTPConnection('127.0.0.1', self.lan.server_port, timeout=3)
        try:
            with self.assertRaises((OSError, ssl.SSLError)):
                connection.request('GET', '/api/health'); connection.getresponse()
        finally: connection.close()
        self.assertEqual(self.executor.ids, [])
        self.assertEqual(self.controller.lan_requests, 0)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK is needed for native client pin tests')
    def test_actual_java_client_pin_acceptance_and_changed_identity_rejection(self):
        subprocess.run(['javac', '-d', self.folder.name, str(ROOT / 'android/src/dev/tossfront/deck/ConnectionTarget.java'), str(ROOT / 'tests/WirelessPinTest.java')], check=True, capture_output=True, timeout=30)
        result = subprocess.run(['java', '-cp', self.folder.name, 'WirelessPinTest', str(self.lan.server_port), self.fingerprint], check=True, capture_output=True, text=True, timeout=15)
        self.assertIn('Verified', result.stdout)
        self.assertEqual(self.executor.ids, [])


if __name__ == '__main__': unittest.main()
