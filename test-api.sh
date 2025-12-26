#!/bin/bash

# Test scripts for Akka Agentic Doc Extractor API
# Usage: ./test-api.sh [scenario]
#
# Scenarios:
#   health          - Health check
#   openai-single   - OpenAI extraction with single file
#   openai-multi     - OpenAI extraction with multiple files
#   claude-single    - Claude extraction with single file
#   claude-multi     - Claude extraction with multiple files
#   openai-custom    - OpenAI with custom prompt
#   claude-custom    - Claude with custom prompt
#   error-no-files   - Error: no files provided
#   error-invalid    - Error: invalid endpoint
#
# Environment variables:
#   BASE_URL         - API base URL (default: http://localhost:8080)
#   FILE1            - Path to first test file (default: sample1.pdf)
#   FILE2            - Path to second test file (default: sample2.pdf)
#   FILE3            - Path to third test file (default: sample3.pdf)

set -e

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
FILE1="${FILE1:-sample1.pdf}"
FILE2="${FILE2:-sample2.pdf}"
FILE3="${FILE3:-sample3.pdf}"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper function to print colored output
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

# Health Check
test_health() {
    print_info "Testing Health Check..."
    curl -X GET "${BASE_URL}/health" \
        -w "\nHTTP Status: %{http_code}\n" \
        -s
    echo ""
}

# OpenAI - Single File
test_openai_single() {
    print_info "Testing OpenAI extraction with single file: ${FILE1}"
    
    if [ ! -f "$FILE1" ]; then
        print_error "File not found: ${FILE1}"
        print_info "Create a test file or set FILE1 environment variable"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract" \
        -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
        -F "files=@${FILE1}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# OpenAI - Multiple Files
test_openai_multi() {
    print_info "Testing OpenAI extraction with multiple files: ${FILE1}, ${FILE2}"
    
    if [ ! -f "$FILE1" ] || [ ! -f "$FILE2" ]; then
        print_error "Files not found: ${FILE1} or ${FILE2}"
        print_info "Create test files or set FILE1 and FILE2 environment variables"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract" \
        -F "prompt=Extract key fields from all documents. Return a combined JSON with data from all files." \
        -F "files=@${FILE1}" \
        -F "files=@${FILE2}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# OpenAI - Three Files
test_openai_three() {
    print_info "Testing OpenAI extraction with three files: ${FILE1}, ${FILE2}, ${FILE3}"
    
    if [ ! -f "$FILE1" ] || [ ! -f "$FILE2" ] || [ ! -f "$FILE3" ]; then
        print_error "Files not found: ${FILE1}, ${FILE2}, or ${FILE3}"
        print_info "Create test files or set FILE1, FILE2, and FILE3 environment variables"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract" \
        -F "prompt=Extract key fields from all three documents. Return a combined JSON array with data from each file." \
        -F "files=@${FILE1}" \
        -F "files=@${FILE2}" \
        -F "files=@${FILE3}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# OpenAI - Custom Prompt
test_openai_custom() {
    print_info "Testing OpenAI extraction with custom prompt: ${FILE1}"
    
    if [ ! -f "$FILE1" ]; then
        print_error "File not found: ${FILE1}"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract" \
        -F "prompt=Extract the following specific fields from this document: invoice_number, invoice_date, total_amount, vendor_name, line_items (as an array with item name and price). Return ONLY valid JSON with no additional text." \
        -F "files=@${FILE1}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# Claude - Single File
test_claude_single() {
    print_info "Testing Claude extraction with single file: ${FILE1}"
    
    if [ ! -f "$FILE1" ]; then
        print_error "File not found: ${FILE1}"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract-claude" \
        -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
        -F "files=@${FILE1}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# Claude - Multiple Files
test_claude_multi() {
    print_info "Testing Claude extraction with multiple files: ${FILE1}, ${FILE2}"
    
    if [ ! -f "$FILE1" ] || [ ! -f "$FILE2" ]; then
        print_error "Files not found: ${FILE1} or ${FILE2}"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract-claude" \
        -F "prompt=Extract key fields from all documents. Return a combined JSON with data from all files." \
        -F "files=@${FILE1}" \
        -F "files=@${FILE2}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# Claude - Three Files
test_claude_three() {
    print_info "Testing Claude extraction with three files: ${FILE1}, ${FILE2}, ${FILE3}"
    
    if [ ! -f "$FILE1" ] || [ ! -f "$FILE2" ] || [ ! -f "$FILE3" ]; then
        print_error "Files not found: ${FILE1}, ${FILE2}, or ${FILE3}"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract-claude" \
        -F "prompt=Extract key fields from all three documents. Return a combined JSON array with data from each file." \
        -F "files=@${FILE1}" \
        -F "files=@${FILE2}" \
        -F "files=@${FILE3}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# Claude - Custom Prompt
test_claude_custom() {
    print_info "Testing Claude extraction with custom prompt: ${FILE1}"
    
    if [ ! -f "$FILE1" ]; then
        print_error "File not found: ${FILE1}"
        return 1
    fi
    
    curl -X POST "${BASE_URL}/extract-claude" \
        -F "prompt=Extract the following specific fields from this receipt: merchant_name, transaction_date, total_amount, tax_amount, payment_method, items (as an array with name and price). Return ONLY valid JSON with no additional text." \
        -F "files=@${FILE1}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\nTime: %{time_total}s\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# Error - No Files
test_error_no_files() {
    print_info "Testing error scenario: No files provided"
    
    curl -X POST "${BASE_URL}/extract" \
        -F "prompt=Extract key fields into JSON." \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\n" \
        -s | jq '.' 2>/dev/null || cat
    echo ""
}

# Error - Invalid Endpoint
test_error_invalid() {
    print_info "Testing error scenario: Invalid endpoint"
    
    curl -X POST "${BASE_URL}/invalid-endpoint" \
        -F "prompt=Test" \
        -F "files=@${FILE1}" \
        -H "Accept: application/json" \
        -w "\n\nHTTP Status: %{http_code}\n" \
        -s
    echo ""
}

# Run all tests
test_all() {
    print_info "Running all test scenarios..."
    echo ""
    
    test_health
    echo "---"
    
    test_openai_single
    echo "---"
    
    test_openai_multi
    echo "---"
    
    test_claude_single
    echo "---"
    
    test_claude_multi
    echo "---"
    
    test_error_no_files
    echo "---"
}

# Main
case "${1:-help}" in
    health)
        test_health
        ;;
    openai-single)
        test_openai_single
        ;;
    openai-multi)
        test_openai_multi
        ;;
    openai-three)
        test_openai_three
        ;;
    openai-custom)
        test_openai_custom
        ;;
    claude-single)
        test_claude_single
        ;;
    claude-multi)
        test_claude_multi
        ;;
    claude-three)
        test_claude_three
        ;;
    claude-custom)
        test_claude_custom
        ;;
    error-no-files)
        test_error_no_files
        ;;
    error-invalid)
        test_error_invalid
        ;;
    all)
        test_all
        ;;
    help|*)
        echo "Usage: $0 [scenario]"
        echo ""
        echo "Scenarios:"
        echo "  health          - Health check"
        echo "  openai-single   - OpenAI extraction with single file"
        echo "  openai-multi    - OpenAI extraction with 2 files"
        echo "  openai-three    - OpenAI extraction with 3 files"
        echo "  openai-custom   - OpenAI with custom prompt"
        echo "  claude-single   - Claude extraction with single file"
        echo "  claude-multi    - Claude extraction with 2 files"
        echo "  claude-three    - Claude extraction with 3 files"
        echo "  claude-custom   - Claude with custom prompt"
        echo "  error-no-files   - Error: no files provided"
        echo "  error-invalid    - Error: invalid endpoint"
        echo "  all             - Run all test scenarios"
        echo ""
        echo "Environment variables:"
        echo "  BASE_URL        - API base URL (default: http://localhost:8080)"
        echo "  FILE1           - Path to first test file (default: sample1.pdf)"
        echo "  FILE2           - Path to second test file (default: sample2.pdf)"
        echo "  FILE3           - Path to third test file (default: sample3.pdf)"
        echo ""
        echo "Examples:"
        echo "  $0 health"
        echo "  $0 openai-multi"
        echo "  FILE1=invoice.pdf FILE2=receipt.pdf $0 openai-multi"
        echo "  BASE_URL=http://localhost:8080 $0 all"
        ;;
esac

