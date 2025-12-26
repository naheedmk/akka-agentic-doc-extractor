#!/bin/bash

# Individual curl command examples for Akka Agentic Doc Extractor API
# These are standalone curl commands that can be copied and executed directly

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=========================================="
echo "Akka Agentic Doc Extractor - Curl Examples"
echo "=========================================="
echo ""
echo "Base URL: ${BASE_URL}"
echo ""
echo "Note: Replace 'sample1.pdf', 'sample2.pdf', etc. with your actual file paths"
echo ""

cat << 'EOF'

# ==========================================
# 1. Health Check
# ==========================================

curl -X GET "${BASE_URL}/health"

# ==========================================
# 2. OpenAI - Single File
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"

# ==========================================
# 3. OpenAI - Multiple Files (2 files)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields from all documents. Return a combined JSON with data from all files." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -H "Accept: application/json"

# ==========================================
# 4. OpenAI - Multiple Files (3 files)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields from all three documents. Return a combined JSON array with data from each file." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -F "files=@sample3.pdf" \
  -H "Accept: application/json"

# ==========================================
# 5. OpenAI - Multiple Files (4+ files)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields from all documents. Return a combined JSON." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -F "files=@sample3.pdf" \
  -F "files=@sample4.pdf" \
  -H "Accept: application/json"

# ==========================================
# 6. OpenAI - Custom Prompt (Single File)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract the following specific fields from this invoice: invoice_number, invoice_date, total_amount, vendor_name, line_items (as an array with item name and price). Return ONLY valid JSON with no additional text." \
  -F "files=@invoice.pdf" \
  -H "Accept: application/json"

# ==========================================
# 7. OpenAI - Custom Prompt (Multiple Files)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract invoice details from all documents. For each invoice, extract: invoice_number, date, total, vendor. Return as a JSON array with one object per invoice." \
  -F "files=@invoice1.pdf" \
  -F "files=@invoice2.pdf" \
  -F "files=@invoice3.pdf" \
  -H "Accept: application/json"

# ==========================================
# 8. OpenAI - Image File (PNG/JPG)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract all text and key information from this image. Return as JSON." \
  -F "files=@document.png" \
  -H "Accept: application/json"

# ==========================================
# 9. OpenAI - Default Prompt (No Prompt)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"

# ==========================================
# 10. Claude - Single File
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"

# ==========================================
# 11. Claude - Multiple Files (2 files)
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "prompt=Extract key fields from all documents. Return a combined JSON with data from all files." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -H "Accept: application/json"

# ==========================================
# 12. Claude - Multiple Files (3 files)
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "prompt=Extract key fields from all three documents. Return a combined JSON array with data from each file." \
  -F "files=@sample1.pdf" \
  -F "files=@sample2.pdf" \
  -F "files=@sample3.pdf" \
  -H "Accept: application/json"

# ==========================================
# 13. Claude - Custom Prompt (Single File)
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "prompt=Extract the following specific fields from this receipt: merchant_name, transaction_date, total_amount, tax_amount, payment_method, items (as an array with name and price). Return ONLY valid JSON with no additional text." \
  -F "files=@receipt.pdf" \
  -H "Accept: application/json"

# ==========================================
# 14. Claude - Custom Prompt (Multiple Files)
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "prompt=Extract receipt details from all documents. For each receipt, extract: merchant, date, total, payment_method. Return as a JSON array with one object per receipt." \
  -F "files=@receipt1.pdf" \
  -F "files=@receipt2.pdf" \
  -F "files=@receipt3.pdf" \
  -H "Accept: application/json"

# ==========================================
# 15. Claude - Image File (PNG/JPG)
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "prompt=Extract all text and key information from this image. Return as JSON." \
  -F "files=@document.jpg" \
  -H "Accept: application/json"

# ==========================================
# 16. Claude - Default Prompt (No Prompt)
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"

# ==========================================
# 17. Error - No Files (OpenAI)
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields into JSON." \
  -H "Accept: application/json"

# ==========================================
# 18. Error - No Files (Claude)
# ==========================================

curl -X POST "${BASE_URL}/extract-claude" \
  -F "prompt=Extract key fields into JSON." \
  -H "Accept: application/json"

# ==========================================
# 19. Error - Invalid Endpoint
# ==========================================

curl -X POST "${BASE_URL}/invalid-endpoint" \
  -F "prompt=Test" \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"

# ==========================================
# 20. Error - Wrong HTTP Method
# ==========================================

curl -X GET "${BASE_URL}/extract" \
  -H "Accept: application/json"

# ==========================================
# Advanced Examples with Verbose Output
# ==========================================

# Verbose output (shows request/response headers)
curl -v -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json"

# Save response to file
curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json" \
  -o response.json

# Pretty print JSON response (requires jq)
curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json" \
  -s | jq '.'

# Show timing information
curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@sample1.pdf" \
  -H "Accept: application/json" \
  -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
  -s | jq '.'

# ==========================================
# Multi-file with Different File Types
# ==========================================

curl -X POST "${BASE_URL}/extract" \
  -F "prompt=Extract key information from all documents. Return combined JSON." \
  -F "files=@document1.pdf" \
  -F "files=@document2.png" \
  -F "files=@document3.jpg" \
  -H "Accept: application/json"

EOF

echo ""
echo "=========================================="
echo "End of Examples"
echo "=========================================="

