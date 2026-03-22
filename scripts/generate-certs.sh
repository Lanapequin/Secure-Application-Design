#!/bin/bash
# =============================================================
# generate-certs.sh
# Genera el keystore PKCS12 para el servidor Spring.
# Ejecutar UNA VEZ antes de compilar.
#
# Uso:
#   chmod +x generate-certs.sh
#   ./generate-certs.sh
#   ./generate-certs.sh 1.2.3.4   # con la IP del servidor
# =============================================================
set -e

HOST="${1:-localhost}"
KEYSTORE_DIR="../spring-server/src/main/resources/keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/serverkeystore.p12"
ALIAS="serverkeypair"
PASS="changeit"

echo ""
echo "=================================================="
echo "  Generando certificado para: $HOST"
echo "=================================================="

mkdir -p "$KEYSTORE_DIR"

# Elimina keystore anterior si existe
[ -f "$KEYSTORE_FILE" ] && rm "$KEYSTORE_FILE" && echo "  Keystore anterior eliminado."

# Genera par de llaves RSA 2048 + certificado auto-firmado
# Valido por 10 años (para desarrollo)
keytool -genkeypair \
  -alias        "$ALIAS" \
  -keyalg       RSA \
  -keysize      2048 \
  -storetype    PKCS12 \
  -keystore     "$KEYSTORE_FILE" \
  -storepass    "$PASS" \
  -keypass      "$PASS" \
  -validity     3650 \
  -dname        "CN=$HOST, OU=ECI, O=ECI-Seguridad, L=Bogota, ST=Cundinamarca, C=CO" \
  -noprompt

echo ""
echo "   Keystore creado: $KEYSTORE_FILE"
echo ""

# Verifica lo generado
echo "  Verificación del keystore:"
keytool -list -v \
  -keystore  "$KEYSTORE_FILE" \
  -storepass "$PASS" \
  -storetype PKCS12 \
  | grep -E "(Alias|Valid|Owner)"

echo ""
echo "=================================================="
echo "   Listo — ahora ejecuta: mvn clean package"
echo "=================================================="