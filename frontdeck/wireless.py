"""Opt-in LAN TLS identity; USB mode keeps using the standard library only."""
from __future__ import annotations

import datetime
import hashlib
import ipaddress
from pathlib import Path
import ssl

LAN_NETWORKS = tuple(ipaddress.ip_network(n) for n in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))

def lan_address(value):
    address = ipaddress.IPv4Address(value)
    if not any(address in network for network in LAN_NETWORKS):
        raise ValueError('무선 연결에는 PC의 사설 IPv4 주소를 지정하세요.')
    return str(address)

def ensure_identity(directory, address):
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import NameOID

    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    certificate, keyfile = directory / 'wireless-cert.pem', directory / 'wireless-key.pem'
    if certificate.exists() != keyfile.exists():
        raise ValueError('무선 인증서와 키를 함께 복원하세요. 기존 연결의 인증서를 자동 교체하지 않습니다.')
    if not certificate.exists():
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'FrontDeck PC')])
        now = datetime.datetime.now(datetime.timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
                .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(minutes=5))
                .not_valid_after(now + datetime.timedelta(days=1825))
                .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address(address)),
                                x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), critical=False)
                .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                .sign(key, hashes.SHA256()))
        keyfile.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                              serialization.NoEncryption()))
        keyfile.chmod(0o600)
        certificate.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    cert = x509.load_pem_x509_certificate(certificate.read_bytes())
    if cert.not_valid_after_utc <= datetime.datetime.now(datetime.timezone.utc):
        raise ValueError('무선 인증서가 만료됐습니다. 새 인증서를 준비하고 기기를 다시 연결하세요.')
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(certificate, keyfile)
    fingerprint = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()
    return context, fingerprint

def confirmation_code(fingerprint):
    return ' '.join(fingerprint[i:i+4].upper() for i in range(0, 16, 4))
