# Akka Java SDK License Key Configuration

## Issue

If you see the error:
```
Akka terminated. Obtain free keys at https://akka.io/key
```

This means the Akka Java SDK requires a license key to run.

## Solution

### Option 1: Get a Free License Key (Recommended if using ComponentClient)

1. Visit https://akka.io/key
2. Sign up for a free license key
3. Set it as an environment variable:
   ```bash
   export AKKA_JAVASDK_LICENSE_KEY="your-license-key-here"
   ```

4. Or add it to `application.conf`:
   ```hocon
   akka.javasdk {
     license-key = "your-license-key-here"
   }
   ```

### Option 2: Use Fallback Direct Clients (Current Implementation)

**Good News:** The application is already configured to use fallback direct clients when `ComponentClient` is not initialized. This means:

- ✅ **Extraction endpoints work without a license key**
- ✅ **No ComponentClient initialization needed**
- ✅ **Direct OpenAI/Claude clients handle all requests**

The fallback is automatically used when `ComponentClient` is null, which is the current state.

## Current Status

Based on the logs, the fallback is working:
```
INFO -- Using fallback direct Claude client (ComponentClient not initialized)
```

This means requests are being processed successfully using the direct clients, **without requiring a license key**.

## When License Key is Needed

You only need a license key if you want to:
- Use the Akka Java SDK Agent component (`ExtractionAgent`)
- Initialize `ComponentClient` properly
- Use SDK runtime features

## Current Architecture

The application uses a **hybrid approach**:

1. **Primary (when ComponentClient available)**: Akka Java SDK Agent component
2. **Fallback (current)**: Direct OpenAI/Claude clients

Both approaches provide the same functionality - document extraction from files.

## Verification

To verify the fallback is working, check the server logs:
- Look for: `"Using fallback direct [OpenAI/Claude] client"`
- Extraction requests should complete successfully
- No license key errors should appear (unless ComponentClient is initialized)

## Removing the License Check

If you want to completely avoid the license check and only use direct clients:

1. The fallback is already in place and working
2. The license check only triggers if ComponentClient is initialized
3. Since ComponentClient is null, the license check should not trigger

However, if you still see license errors, you can:
- Get a free license key (Option 1)
- Or ensure ComponentClient is never initialized (already the case)

## Summary

**You don't need a license key for the current implementation** because:
- ComponentClient is not initialized (null)
- Fallback direct clients are used
- Extraction endpoints work without SDK runtime

The license key is only needed if you want to use the Akka Java SDK Agent component with ComponentClient.

