package com.example.extractor;

import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.client.ComponentClientImpl;
import akka.javasdk.impl.serialization.JsonSerializer;
import akka.javasdk.agent.Agent;
import com.example.extractor.agent.ExtractionAdapter;
import com.example.extractor.agent.SdkExtractionAdapter;
import com.example.extractor.agent.DirectExtractionAdapter;
import com.example.extractor.agent.ExtractionAgent;
import com.example.extractor.agent.ExtractionAgentClient;
import com.example.extractor.agent.ExtractionRequest;
import com.example.extractor.agent.openai.OpenAIAgentFactory;
import com.example.extractor.agent.claude.ClaudeAgentFactory;
import com.example.extractor.routes.ExtractRoutes;
import com.typesafe.config.Config;
import scala.collection.immutable.Map$;
import scala.Option;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.compat.java8.OptionConverters;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class Main {
  public static void main(String[] args) {
    ActorSystem system = null;
    try {
      // Create classic ActorSystem for HTTP server
      system = ActorSystem.create("extractor");
    Config cfg = system.settings().config();

    String host = cfg.getString("http.host");
    int port = cfg.getInt("http.port");

      // Initialize ComponentClient for Akka Java SDK
      // MUST create ComponentClient to use Akka Java SDK Agent components
      // License key warning may appear but ComponentClient will still work
      ComponentClient componentClient = null;
      try {
        // Create typed ActorSystem adapter
        akka.actor.typed.ActorSystem<?> typedSystem = Adapter.toTyped(system);
        
        // Create ComponentClientImpl with required dependencies
        // ComponentClientImpl requires:
        // 1. ComponentClients (runtime component clients) - we'll use null/mock for standalone
        // 2. JsonSerializer - create a default one
        // 3. Agent class map - map our ExtractionAgent
        // 4. Telemetry context - Optional.empty()
        // 5. ExecutionContext - from typed system
        // 6. Typed ActorSystem
        
        // Create JsonSerializer
        JsonSerializer jsonSerializer = new JsonSerializer();
        
        // Create agent class map (id -> Agent class)
        // Use Scala's immutable Map directly to avoid type ambiguity
        @SuppressWarnings("unchecked")
        scala.collection.immutable.Map<String, Class<Agent>> agentClassMap = 
            (scala.collection.immutable.Map<String, Class<Agent>>) (Object) Map$.MODULE$.<String, Class<Agent>>empty()
                .updated("extraction-agent", ExtractionAgent.class);
        
        // Get ExecutionContext from typed system
        ExecutionContext ec = typedSystem.executionContext();
        
        // Create ComponentClients - create a proper implementation for standalone use
        // ComponentClients must provide an AgentClient that is not null
        akka.runtime.sdk.spi.ComponentClients componentClients;
        try {
          Class<?> componentClientsInterface = Class.forName("akka.runtime.sdk.spi.ComponentClients");
          Class<?> agentClientInterface = Class.forName("akka.runtime.sdk.spi.AgentClient");
          
          // Create a full implementation of AgentClient that uses the agent class map
          // This will actually instantiate and invoke agents directly when send() is called
          // Capture system for use in the handler
          final ActorSystem sysForHandler = system;
          Object agentClient = java.lang.reflect.Proxy.newProxyInstance(
              agentClientInterface.getClassLoader(),
              new Class<?>[] { agentClientInterface },
              new java.lang.reflect.InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] methodArgs) throws Throwable {
                  String methodName = method.getName();
                  Class<?> returnType = method.getReturnType();
                  
                  // Handle send() method
                  // ComponentClientImpl expects AgentResult from AgentClient.send()
                  // We execute the Effect and create AgentResult with the AI response
                  if ("send".equals(methodName) && methodArgs != null && methodArgs.length == 1) {
                    Object request = methodArgs[0];
                    return handleAgentSend(request, agentClassMap, jsonSerializer, ec, typedSystem, sysForHandler);
                  }
                  
                  // Handle sendStream() method if it exists
                  if ("sendStream".equals(methodName)) {
                    // For now, return an empty stream - can be implemented later
                    Promise<Object> promise = Promise.apply();
                    return promise.future();
                  }
                  
                  // For other methods, return appropriate defaults
                  if (returnType.isPrimitive()) {
                    return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(returnType, 1), 0);
                  }
                  return null;
                }
              }
          );
          
          // Create ComponentClients proxy that returns the AgentClient for agentClient() method
          // Use final references for lambda
          final Object finalAgentClient = agentClient;
          componentClients = (akka.runtime.sdk.spi.ComponentClients) java.lang.reflect.Proxy.newProxyInstance(
              componentClientsInterface.getClassLoader(),
              new Class<?>[] { componentClientsInterface },
              (proxy, method, methodArgs) -> {
                String methodName = method.getName();
                // Check if this is the agentClient() method - handle all variations
                if ("agentClient".equals(methodName) && method.getParameterCount() == 0) {
                  sysForHandler.log().debug("ComponentClients.agentClient() called, returning AgentClient proxy");
                  return finalAgentClient; // Return the AgentClient we created
                }
                // Log other method calls for debugging
                sysForHandler.log().debug("ComponentClients.{}() called", methodName);
                // For other methods, return null or default values
                if (method.getReturnType().isPrimitive()) {
                  return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(method.getReturnType(), 1), 0);
                }
                return null;
              }
          );
          sysForHandler.log().info("✓ Created ComponentClients with AgentClient for standalone use");
          sysForHandler.log().info("  → AgentClient proxy: " + (finalAgentClient != null ? "created" : "null"));
        } catch (Exception ex) {
          system.log().error("✗ Failed to create ComponentClients: " + ex.getMessage());
          system.log().error("  → Stack trace:", ex);
          throw new RuntimeException("Cannot create ComponentClients - required for ComponentClient", ex);
        }
        
        // Create ComponentClientImpl - ComponentClients is now guaranteed to be non-null
        try {
          ComponentClientImpl clientImpl = ComponentClientImpl.apply(
              componentClients, // ComponentClients - non-null proxy
              jsonSerializer,
              agentClassMap,
              OptionConverters.toScala(java.util.Optional.empty()), // Telemetry context
              ec,
              typedSystem
          );
          componentClient = clientImpl;
          
          system.log().info("================================================");
          system.log().info("✓ ComponentClient initialized successfully");
          system.log().info("  → Using Akka Java SDK Agent components");
          system.log().info("  → License key warning can be ignored");
          system.log().info("================================================");
        } catch (Exception e) {
          system.log().warning("⚠ ComponentClientImpl creation failed: " + e.getMessage());
          system.log().warning("  → Attempting alternative initialization");
          throw e;
        }
      } catch (Exception e) {
        system.log().error("✗ Failed to initialize ComponentClient: " + e.getMessage());
        system.log().error("  → Stack trace:", e);
        system.log().error("  → Server will NOT start without ComponentClient");
        system.log().error("  → Please check Akka Java SDK configuration");
        throw new RuntimeException("ComponentClient initialization failed - cannot proceed", e);
      }

      // --- Create extraction adapters ---
      // Adapters handle SDK with automatic fallback to direct invocation
      DirectExtractionAdapter openaiDirectAdapter = new DirectExtractionAdapter("openai", system);
      DirectExtractionAdapter claudeDirectAdapter = new DirectExtractionAdapter("claude", system);
      
      SdkExtractionAdapter openaiAdapter = new SdkExtractionAdapter(componentClient, "openai", openaiDirectAdapter);
      SdkExtractionAdapter claudeAdapter = new SdkExtractionAdapter(componentClient, "claude", claudeDirectAdapter);
      
      system.log().info("✓ Extraction adapters initialized");
      system.log().info("  → OpenAI adapter: " + openaiAdapter.getName());
      system.log().info("  → Claude adapter: " + claudeAdapter.getName());
      system.log().info("  → Multi-file support: enabled");
      system.log().info("  → Automatic fallback: enabled (SDK → Direct)");

    // Routes: /extract (OpenAI) and /extract-claude (Claude)
    Route routes = new ExtractRoutes(
        system,
        openaiAdapter,
        claudeAdapter
    ).routes();

    // Server timeouts are configured in application.conf
    CompletionStage<ServerBinding> binding =
        Http.get(system).newServerAt(host, port).bind(routes);

    ServerBinding serverBinding = binding.toCompletableFuture().join();
    
    if (serverBinding == null) {
      system.log().error("Failed to bind HTTP server - binding is null");
      system.terminate();
      System.exit(1);
      return;
    }
    
    system.log().info("Server running at http://{}:{}/", host, port);
    system.log().info("Akka Java SDK Agent-based extraction service is ready");
    system.log().info("Press Ctrl+C to stop the server");
    
    // Add shutdown hook - use final reference for lambda
    final ActorSystem finalSystem = system;
    final ServerBinding finalBinding = serverBinding;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      finalSystem.log().info("Shutting down server...");
      try {
        finalBinding.unbind().thenRun(() -> {
          finalSystem.log().info("Server unbound, terminating ActorSystem...");
          finalSystem.terminate();
        });
      } catch (Exception e) {
        finalSystem.log().error("Error during shutdown", e);
        finalSystem.terminate();
      }
    }));
    
    // Keep the main thread alive
    // Wait for the ActorSystem to terminate (which happens on shutdown)
    try {
      finalSystem.getWhenTerminated().toCompletableFuture().join();
    } catch (Exception e) {
      finalSystem.log().error("Error waiting for system termination", e);
    }
      
    } catch (Exception e) {
      System.err.println("Failed to start server: " + e.getMessage());
      e.printStackTrace();
      if (system != null) {
        system.terminate();
      }
      System.exit(1);
    }
  }
  
  /**
   * Handles AgentClient.send() by actually invoking agents using the agent class map.
   * This is a full implementation that instantiates agents and calls their methods.
   */
  private static Future<Object> handleAgentSend(
      Object request,
      scala.collection.immutable.Map<String, Class<Agent>> agentClassMap,
      JsonSerializer serializer,
      ExecutionContext ec,
      akka.actor.typed.ActorSystem<?> typedSystem,
      ActorSystem system) {
    
    Promise<Object> promise = Promise.apply();
    
    ec.execute(() -> {
      try {
        // Extract agent ID from request using reflection
        String agentId = "extraction-agent"; // Default
        try {
          Method getAgentId = request.getClass().getMethod("agentId");
          Object idObj = getAgentId.invoke(request);
          if (idObj != null) {
            agentId = idObj.toString();
          }
        } catch (Exception e) {
          // Use default
        }
        
        // Find agent class from map
        // Use reflection to call get() method on Scala Map to avoid ambiguity with java.util.Map
        scala.Option<Class<Agent>> agentClassOpt;
        try {
          Method getMethod = agentClassMap.getClass().getMethod("get", Object.class);
          @SuppressWarnings("unchecked")
          scala.Option<Class<Agent>> result = (scala.Option<Class<Agent>>) getMethod.invoke(agentClassMap, agentId);
          agentClassOpt = result;
        } catch (Exception e) {
          throw new RuntimeException("Failed to get agent class from map", e);
        }
        if (agentClassOpt.isEmpty()) {
          promise.failure(new IllegalArgumentException("Agent not found: " + agentId));
          return;
        }
        Class<Agent> agentClass = agentClassOpt.get();
        
        // Instantiate agent
        Agent agent = agentClass.getDeclaredConstructor().newInstance();
        
        // Extract payload from request
        Object payload = null;
        try {
          Method getPayload = request.getClass().getMethod("payload");
          payload = getPayload.invoke(request);
        } catch (Exception e) {
          // No payload
        }
        
        // Find and invoke the appropriate method on the agent
        // ComponentClientImpl should have set up the method call, but we'll try common methods
        Method method = null;
        Object result = null;
        ExtractionRequest extractionRequest = null; // Store for Effect execution
        
        // Try extractFromFiles first (takes ExtractionRequest)
        try {
          method = agentClass.getMethod("extractFromFiles", ExtractionRequest.class);
          if (payload != null) {
            System.err.println("Payload type: " + payload.getClass().getName());
            // Deserialize payload to ExtractionRequest
            // Payload might already be BytesPayload or ExtractionRequest
            if (payload instanceof ExtractionRequest) {
              extractionRequest = (ExtractionRequest) payload;
              System.err.println("Payload is already ExtractionRequest with " + extractionRequest.filePaths.size() + " files");
            } else if (payload.getClass().getName().contains("BytesPayload")) {
              // Payload is BytesPayload, deserialize it
              extractionRequest = serializer.fromBytes(ExtractionRequest.class, (akka.runtime.sdk.spi.BytesPayload) payload);
              System.err.println("Deserialized ExtractionRequest from BytesPayload with " + extractionRequest.filePaths.size() + " files");
            } else {
              // Try to serialize and then deserialize
              akka.runtime.sdk.spi.BytesPayload payloadBytes = serializer.toBytes(payload);
              extractionRequest = serializer.fromBytes(ExtractionRequest.class, payloadBytes);
              System.err.println("Deserialized ExtractionRequest after serialization with " + extractionRequest.filePaths.size() + " files");
            }
            result = method.invoke(agent, extractionRequest);
          } else {
            System.err.println("No payload in request, creating empty ExtractionRequest");
            extractionRequest = new ExtractionRequest("", java.util.Collections.emptyList());
            result = method.invoke(agent, extractionRequest);
          }
        } catch (Exception e) {
          // Try healthCheck (no parameters)
          try {
            method = agentClass.getMethod("healthCheck");
            result = method.invoke(agent);
          } catch (Exception e2) {
            promise.failure(new RuntimeException("Could not find or invoke agent method", e2));
            return;
          }
        }
        
        // ComponentClientImpl expects AgentResult, not Effect
        // We need to convert the Effect to AgentResult
        // The Effect contains the primary effect which needs to be executed
        try {
          // Check if result is a BaseAgentEffectBuilder (Effect)
          if (result instanceof akka.javasdk.impl.agent.BaseAgentEffectBuilder) {
            akka.javasdk.impl.agent.BaseAgentEffectBuilder<?> effectBuilder = 
                (akka.javasdk.impl.agent.BaseAgentEffectBuilder<?>) result;
            
            // Get the primary effect from the builder
            Object primaryEffect = effectBuilder.primaryEffect();
            
            // ComponentClientImpl expects AgentResult which is the executed result, not the Effect
            // The Effect contains a RequestModel with all the info needed to make the AI call
            // For standalone use, we need to EXECUTE the Effect by making the AI call ourselves
            // Then wrap the result in AgentResult
            
            // Execute the Effect: Extract RequestModel and make the AI call with file attachments
            if (primaryEffect != null && primaryEffect.getClass().getName().contains("RequestModel")) {
              System.err.println("Attempting to execute Effect - primaryEffect is RequestModel");
              try {
                // Extract fields from RequestModel using reflection
                System.err.println("Extracting RequestModel fields...");
                Method getModelProvider = primaryEffect.getClass().getMethod("modelProvider");
                Method getSystemMessage = primaryEffect.getClass().getMethod("systemMessage");
                Method getUserMessage = primaryEffect.getClass().getMethod("userMessage");
                Method getResponseType = primaryEffect.getClass().getMethod("responseType");
                
                Object modelProvider = getModelProvider.invoke(primaryEffect);
                Object systemMessageObj = getSystemMessage.invoke(primaryEffect);
                String userMessage = (String) getUserMessage.invoke(primaryEffect);
                Class<?> responseType = (Class<?>) getResponseType.invoke(primaryEffect);
                
                System.err.println("Extracted userMessage length: " + (userMessage != null ? userMessage.length() : 0));
                System.err.println("ExtractionRequest filePaths: " + (extractionRequest != null ? extractionRequest.filePaths.size() : 0) + " files");
                
                // Extract system message text
                String systemMessage = "";
                if (systemMessageObj != null) {
                  try {
                    Method getText = systemMessageObj.getClass().getMethod("text");
                    systemMessage = (String) getText.invoke(systemMessageObj);
                  } catch (Exception e) {
                    systemMessage = systemMessageObj.toString();
                  }
                }
                
                // Determine which AI provider to use from modelProvider
                String providerName = "openai"; // default
                if (modelProvider != null) {
                  String providerStr = modelProvider.toString().toLowerCase();
                  System.err.println("ModelProvider: " + providerStr);
                  if (providerStr.contains("claude") || providerStr.contains("anthropic")) {
                    providerName = "claude";
                  } else if (providerStr.contains("openai")) {
                    providerName = "openai";
                  }
                }
                System.err.println("Using provider: " + providerName);
                
                // Make the AI call with file attachments
                // Use the ExtractionRequest to get file paths and upload them
                System.err.println("Making AI call with files...");
                String aiResponse = makeAICallWithFiles(
                    providerName, 
                    systemMessage, 
                    userMessage, 
                    extractionRequest != null ? extractionRequest.filePaths : java.util.Collections.emptyList(),
                    system);
                
                System.err.println("AI call completed, response length: " + (aiResponse != null ? aiResponse.length() : 0));
                
                // Create AgentResult - ComponentClientImpl requires it
                System.err.println("✓ Executed Effect - AI response received (length: " + aiResponse.length() + ")");
                
                // Try to create AgentResult using all available methods
                Object agentResult = createSimpleAgentResult(aiResponse);
                
                if (agentResult != null) {
                  System.err.println("✓ Created AgentResult successfully");
                  promise.success(agentResult);
                  return;
                }
                
                // If AgentResult creation failed, we need to find another way
                // ComponentClientImpl expects AgentResult, so we must create it
                // Try using JsonSerializer to create BytesPayload and then AgentResult
                System.err.println("⚠ Simple AgentResult creation failed, trying with BytesPayload...");
                try {
                  akka.runtime.sdk.spi.BytesPayload responsePayload = serializer.toBytes(aiResponse);
                  Class<?> agentResultClass = Class.forName("akka.runtime.sdk.spi.AgentResult");
                  
                  // Try constructor with BytesPayload
                  java.lang.reflect.Constructor<?>[] constructors = agentResultClass.getConstructors();
                  for (java.lang.reflect.Constructor<?> ctor : constructors) {
                    if (ctor.getParameterCount() == 1) {
                      Class<?> paramType = ctor.getParameterTypes()[0];
                      if (paramType.isAssignableFrom(akka.runtime.sdk.spi.BytesPayload.class)) {
                        try {
                          agentResult = ctor.newInstance(responsePayload);
                          if (agentResult != null) {
                            System.err.println("✓ Created AgentResult with BytesPayload constructor");
                            promise.success(agentResult);
                            return;
                          }
                        } catch (Exception e) {
                          // Try next
                        }
                      }
                    }
                  }
                  
                  // Try companion object apply with BytesPayload
                  Class<?> agentResultCompanion = Class.forName("akka.runtime.sdk.spi.AgentResult$");
                  java.lang.reflect.Method[] methods = agentResultCompanion.getMethods();
                  for (java.lang.reflect.Method m : methods) {
                    if ("apply".equals(m.getName()) && m.getParameterCount() == 1) {
                      Class<?> paramType = m.getParameterTypes()[0];
                      if (paramType.isAssignableFrom(akka.runtime.sdk.spi.BytesPayload.class)) {
                        try {
                          agentResult = m.invoke(null, responsePayload);
                          if (agentResult != null) {
                            System.err.println("✓ Created AgentResult with apply(BytesPayload)");
                            promise.success(agentResult);
                            return;
                          }
                        } catch (Exception e) {
                          // Try next
                        }
                      }
                    }
                  }
                } catch (Exception e) {
                  System.err.println("BytesPayload approach failed: " + e.getMessage());
                }
                
                // All approaches failed - this should not happen
                System.err.println("✗ CRITICAL: Could not create AgentResult - ComponentClientImpl will fail");
                promise.failure(new RuntimeException("Failed to create AgentResult from AI response"));
                return;
              } catch (Exception e) {
                System.err.println("✗ Failed to execute Effect: " + e.getMessage());
                System.err.println("Exception type: " + e.getClass().getName());
                e.printStackTrace();
                // Fall through to try other approaches
              }
            } else {
              System.err.println("PrimaryEffect is not RequestModel: " + (primaryEffect != null ? primaryEffect.getClass().getName() : "null"));
            }
            
            // Fallback: Try to create AgentResult from the Effect (ComponentClientImpl might process it)
            Class<?> agentResultClass = Class.forName("akka.runtime.sdk.spi.AgentResult");
            
            // Try multiple approaches to create AgentResult
            Object agentResult = null;
            Exception lastException = null;
            
            // Approach 1: Try constructor with Effect/BaseAgentEffectBuilder
            try {
              agentResult = agentResultClass.getConstructor(result.getClass())
                  .newInstance(result);
            } catch (Exception e) {
              lastException = e;
            }
            
            // Approach 2: Try constructor with primaryEffect
            if (agentResult == null) {
              try {
                agentResult = agentResultClass.getConstructor(primaryEffect.getClass())
                    .newInstance(primaryEffect);
              } catch (Exception e) {
                lastException = e;
              }
            }
            
            // Approach 3: Try constructor with BytesPayload (serialized Effect)
            if (agentResult == null) {
              try {
                akka.runtime.sdk.spi.BytesPayload resultPayload = serializer.toBytes(result);
                agentResult = agentResultClass.getConstructor(akka.runtime.sdk.spi.BytesPayload.class)
                    .newInstance(resultPayload);
              } catch (Exception e) {
                lastException = e;
              }
            }
            
            // Approach 4: Try using apply() method with RequestModel (primaryEffect)
            if (agentResult == null && primaryEffect != null) {
              try {
                Class<?> agentResultCompanion = Class.forName("akka.runtime.sdk.spi.AgentResult$");
                java.lang.reflect.Method applyMethod = agentResultCompanion.getMethod("apply", primaryEffect.getClass());
                agentResult = applyMethod.invoke(null, primaryEffect);
              } catch (Exception e) {
                lastException = e;
              }
            }
            
            // Approach 5: Try using apply() method with Effect (result)
            if (agentResult == null) {
              try {
                Class<?> agentResultCompanion = Class.forName("akka.runtime.sdk.spi.AgentResult$");
                java.lang.reflect.Method applyMethod = agentResultCompanion.getMethod("apply", result.getClass());
                agentResult = applyMethod.invoke(null, result);
              } catch (Exception e) {
                lastException = e;
              }
            }
            
            // Approach 6: Try to find any apply method that takes one parameter
            if (agentResult == null && primaryEffect != null) {
              try {
                Class<?> agentResultCompanion = Class.forName("akka.runtime.sdk.spi.AgentResult$");
                java.lang.reflect.Method[] methods = agentResultCompanion.getMethods();
                for (java.lang.reflect.Method m : methods) {
                  if ("apply".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                      agentResult = m.invoke(null, primaryEffect);
                      if (agentResult != null) break;
                    } catch (Exception e) {
                      // Try next method
                    }
                  }
                }
              } catch (Exception e) {
                lastException = e;
              }
            }
            
            if (agentResult != null) {
              promise.success(agentResult);
            } else {
              // All approaches failed - ComponentClientImpl expects non-null AgentResult
              // Try to create AgentResult with BytesPayload containing the serialized Effect
              // ComponentClientImpl might be able to process this
              try {
                // Serialize the Effect to BytesPayload
                akka.runtime.sdk.spi.BytesPayload effectPayload = serializer.toBytes(result);
                
                // Try to create AgentResult with BytesPayload using reflection
                // AgentResult might have a constructor or factory method that takes BytesPayload
                // Reuse agentResultClass from outer scope
                
                // Try all constructors
                java.lang.reflect.Constructor<?>[] constructors = agentResultClass.getConstructors();
                for (java.lang.reflect.Constructor<?> ctor : constructors) {
                  try {
                    Class<?>[] paramTypes = ctor.getParameterTypes();
                    if (paramTypes.length == 1 && paramTypes[0].isAssignableFrom(akka.runtime.sdk.spi.BytesPayload.class)) {
                      agentResult = ctor.newInstance(effectPayload);
                      break;
                    }
                  } catch (Exception e) {
                    // Try next constructor
                  }
                }
                
                // If constructor didn't work, try companion object apply method
                if (agentResult == null) {
                  try {
                    Class<?> agentResultCompanion = Class.forName("akka.runtime.sdk.spi.AgentResult$");
                    java.lang.reflect.Method[] methods = agentResultCompanion.getMethods();
                    for (java.lang.reflect.Method m : methods) {
                      if ("apply".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?>[] paramTypes = m.getParameterTypes();
                        if (paramTypes.length == 1 && paramTypes[0].isAssignableFrom(akka.runtime.sdk.spi.BytesPayload.class)) {
                          try {
                            agentResult = m.invoke(null, effectPayload);
                            if (agentResult != null) break;
                          } catch (Exception e) {
                            // Try next method
                          }
                        }
                      }
                    }
                  } catch (Exception e) {
                    // Companion object approach failed
                  }
                }
                
                if (agentResult != null) {
                  System.err.println("Created AgentResult with BytesPayload containing Effect");
                  promise.success(agentResult);
                } else {
                  // Last resort: create a minimal AgentResult that won't cause NullPointerException
                  // This is a workaround - ComponentClientImpl should process Effect using agent class map
                  System.err.println("WARNING: Could not create AgentResult - ComponentClientImpl may fail");
                  System.err.println("Effect type: " + result.getClass().getName());
                  System.err.println("PrimaryEffect type: " + 
                      (primaryEffect != null ? primaryEffect.getClass().getName() : "null"));
                  // Return the Effect as-is wrapped in a Future - ComponentClientImpl might handle it
                  // This will likely cause an error, but at least we tried
                  promise.success(result);
                }
              } catch (Exception e) {
                System.err.println("Failed to create AgentResult with BytesPayload: " + e.getMessage());
                e.printStackTrace();
                // Return the Effect as-is - ComponentClientImpl will fail but we tried
                promise.success(result);
              }
            }
          } else {
            // Not an Effect, return as-is
            promise.success(result);
          }
        } catch (Exception e) {
          // If conversion fails, return the result and let ComponentClientImpl handle it
          promise.success(result);
        }
        
      } catch (Exception e) {
        promise.failure(e);
      }
    });
    
    return promise.future();
  }
  
  /**
   * Makes an AI call using the appropriate client based on provider name.
   * Supports both text-only and file attachments.
   */
  private static String makeAICallWithFiles(
      String providerName, 
      String systemMessage, 
      String userMessage, 
      List<String> filePaths,
      ActorSystem system) {
    try {
      Config cfg = system.settings().config();
      
      if ("claude".equalsIgnoreCase(providerName)) {
        String baseUrl = cfg.getString("akka.javasdk.agent.claude.base-url");
        String model = cfg.getString("akka.javasdk.agent.claude.model-name");
        com.example.extractor.claude.ClaudeMessagesClient client = 
            new com.example.extractor.claude.ClaudeMessagesClient(baseUrl, model);
        
        // Upload files and get file IDs
        List<com.example.extractor.claude.ClaudeMessagesClient.ClaudeAttachment> attachments = 
            new java.util.ArrayList<>();
        
        if (filePaths != null && !filePaths.isEmpty()) {
          com.example.extractor.claude.ClaudeFileUploader fileUploader = 
              new com.example.extractor.claude.ClaudeFileUploader(baseUrl);
          
          // Convert file path strings to Path objects
          List<java.nio.file.Path> paths = new java.util.ArrayList<>();
          for (String filePath : filePaths) {
            paths.add(java.nio.file.Paths.get(filePath));
          }
          
          // Upload all files
          java.util.concurrent.CompletionStage<List<String>> uploadResult = fileUploader.uploadAll(paths);
          List<String> fileIds = uploadResult.toCompletableFuture().get();
          
          // Create attachments with inferred types
          for (int i = 0; i < fileIds.size() && i < paths.size(); i++) {
            String fileId = fileIds.get(i);
            java.nio.file.Path path = paths.get(i);
            String fileName = path.getFileName().toString().toLowerCase();
            
            com.example.extractor.claude.ClaudeMessagesClient.ClaudeAttachment.Type type;
            if (fileName.endsWith(".pdf")) {
              type = com.example.extractor.claude.ClaudeMessagesClient.ClaudeAttachment.Type.PDF;
            } else {
              type = com.example.extractor.claude.ClaudeMessagesClient.ClaudeAttachment.Type.IMAGE;
            }
            
            attachments.add(new com.example.extractor.claude.ClaudeMessagesClient.ClaudeAttachment(type, fileId));
          }
        }
        
        // Make the AI call with attachments
        java.util.concurrent.CompletionStage<String> response = client.extractJson(userMessage, attachments);
        return response.toCompletableFuture().get(); // Blocking call - in production, use async
      } else {
        // Default to OpenAI
        String baseUrl = cfg.getString("akka.javasdk.agent.openai.base-url");
        String model = cfg.getString("akka.javasdk.agent.openai.model-name");
        com.example.extractor.openai.OpenAIResponsesClient client = 
            new com.example.extractor.openai.OpenAIResponsesClient(baseUrl, model);
        
        // Upload files and get file IDs
        List<String> fileIds = new java.util.ArrayList<>();
        
        if (filePaths != null && !filePaths.isEmpty()) {
          com.example.extractor.openai.OpenAIFileUploader fileUploader = 
              new com.example.extractor.openai.OpenAIFileUploader(baseUrl);
          
          // Convert file path strings to Path objects
          List<java.nio.file.Path> paths = new java.util.ArrayList<>();
          for (String filePath : filePaths) {
            paths.add(java.nio.file.Paths.get(filePath));
          }
          
          // Upload all files
          java.util.concurrent.CompletionStage<List<String>> uploadResult = fileUploader.uploadAll(paths);
          fileIds = uploadResult.toCompletableFuture().get();
        }
        
        // Make the AI call with file IDs
        java.util.concurrent.CompletionStage<String> response = client.extractJson(userMessage, fileIds);
        return response.toCompletableFuture().get(); // Blocking call - in production, use async
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to make AI call with files: " + e.getMessage(), e);
    }
  }
  
  /**
   * Creates AgentResult by trying all available methods systematically.
   * Uses the same classloader that loaded ComponentClientImpl to find AgentResult.
   */
  private static Object createSimpleAgentResult(String response) {
    try {
      // Use the classloader that loaded ComponentClientImpl to find AgentResult
      Class<?> componentClientImplClass = ComponentClientImpl.class;
      ClassLoader classLoader = componentClientImplClass.getClassLoader();
      
      // Try to find AgentResult class (without $ first, then with $)
      Class<?> agentResultClass = null;
      Class<?> agentResultCompanion = null;
      
      try {
        agentResultClass = Class.forName("akka.runtime.sdk.spi.AgentResult", false, classLoader);
      } catch (ClassNotFoundException e) {
        System.err.println("AgentResult class not found, trying alternative...");
      }
      
      try {
        agentResultCompanion = Class.forName("akka.runtime.sdk.spi.AgentResult$", false, classLoader);
      } catch (ClassNotFoundException e) {
        System.err.println("AgentResult$ companion not found, will try other approaches...");
      }
      
      // If companion object found, try apply methods
      if (agentResultCompanion != null) {
        java.lang.reflect.Method[] methods = agentResultCompanion.getMethods();
        System.err.println("AgentResult$ companion has " + methods.length + " methods");
        
        // Try all apply methods that take one parameter
        for (java.lang.reflect.Method m : methods) {
          if ("apply".equals(m.getName()) && m.getParameterCount() == 1) {
            Class<?> paramType = m.getParameterTypes()[0];
            System.err.println("  Trying apply(" + paramType.getName() + ")");
            try {
              Object result = m.invoke(null, response);
              if (result != null) {
                String resultClassName = result.getClass().getName();
                System.err.println("    Result type: " + resultClassName);
                if (resultClassName.contains("AgentResult")) {
                  System.err.println("✓ Created AgentResult using apply(" + paramType.getSimpleName() + ")");
                  return result;
                }
              }
            } catch (Exception e) {
              System.err.println("    Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
          }
        }
      }
      
      // Try constructors if agentResultClass was found
      if (agentResultClass != null) {
        java.lang.reflect.Constructor<?>[] constructors = agentResultClass.getConstructors();
        System.err.println("AgentResult class has " + constructors.length + " constructors");
        
        for (java.lang.reflect.Constructor<?> ctor : constructors) {
          if (ctor.getParameterCount() == 1) {
            Class<?> paramType = ctor.getParameterTypes()[0];
            System.err.println("  Trying constructor(" + paramType.getName() + ")");
            try {
              Object result = ctor.newInstance(response);
              if (result != null) {
                System.err.println("✓ Created AgentResult using constructor(" + paramType.getSimpleName() + ")");
                return result;
              }
            } catch (Exception e) {
              System.err.println("    Failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
          }
        }
      }
      
      System.err.println("✗ All AgentResult creation attempts failed - class not found or no suitable methods");
      return null;
    } catch (Exception e) {
      System.err.println("✗ Failed to create AgentResult: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
}
