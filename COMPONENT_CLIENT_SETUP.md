# ComponentClient and License Key Configuration

## Current Status ✅

**Good News:** The application is working with fallback direct clients!

The server:
- ✅ Starts successfully
- ✅ Health endpoint works: `GET /health`
- ✅ Extraction endpoints work using fallback direct clients
- ✅ No ComponentClient initialization needed for current functionality

## Architecture

The application uses a **hybrid approach**:

1. **Primary (when ComponentClient available)**: Akka Java SDK Agent component
2. **Fallback (current, working)**: Direct OpenAI/Claude clients

Both provide the same functionality - document extraction from files.

## License Key Requirement

If you see:
```
Akka terminated. Obtain free keys at https://akka.io/key
```

This means the Akka Java SDK requires a license key. However:

- **You don't need a license key for the current implementation** because:
  - ComponentClient is not initialized (null)
  - Fallback direct clients are used
  - Extraction endpoints work without SDK runtime

- **You only need a license key if**:
  - You want to use the Akka Java SDK Agent component
  - You want to initialize ComponentClient
  - You want to use SDK runtime features

### Getting a Free License Key (Optional)

If you want to use ComponentClient in the future:

1. Visit https://akka.io/key
2. Sign up for a free license key
3. Set it as an environment variable:
   ```bash
   export AKKA_JAVASDK_LICENSE_KEY="your-license-key-here"
   ```

4. Or add it to `application.conf`:
   ```hocon
   akka.javasdk {
     license-key = ${?AKKA_JAVASDK_LICENSE_KEY}
   }
   ```

## Current Implementation

The fallback is automatically used when `ComponentClient` is null. You'll see in the logs:
```
INFO -- Using fallback direct [OpenAI/Claude] client (ComponentClient not initialized)
```

This means requests are being processed successfully **without requiring a license key**.

## Verification

To verify everything is working:
```bash
# Health check
curl http://localhost:8080/health

# Extraction (should work with fallback)
curl -X POST "http://localhost:8080/extract" \
  -F "prompt=Extract key fields into JSON. Return ONLY JSON." \
  -F "files=@your-file.pdf" \
  -H "Accept: application/json"
```

## Summary

- ✅ **Current implementation works without license key**
- ✅ **Fallback direct clients handle all requests**
- ✅ **No ComponentClient initialization needed**
- ℹ️ **License key only needed if using SDK Agent component**

