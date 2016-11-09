# Cordova—Android源码分析二：JS调用Native
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在CordovaWebView初始化时，会根据Android版本的不同，初始化不同的JS调用Native的方法，当Android版本小于4.2(API 17)时，会采用prompt的方式处理JS的调用，当Android版本大于4.2时，会采用JavaScriptInterface的方式调用，初始化的方法在CordovaWebViewImpl的init(CordovaInterface cordova, List<PluginEntry> pluginEntries, CordovaPreferences preferences)方法中的：

    engine.init(this, cordova, engineClient, resourceApi, pluginManager, nativeToJsMessageQueue);

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;engine的类型为CordovaWebViewEngine接口，它的实现类为SystemWebViewEngine，在init()方法的最后一行调用了exposeJsInterface()方法：

    exposeJsInterface(webView, bridge);

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在exposeJsInterface()方法中，根据Android版本的不同，初始化了不同的JS调用Android的方式：

    private static void exposeJsInterface(WebView webView, CordovaBridge bridge) {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            Log.i(TAG, "Disabled addJavascriptInterface() bridge since Android version is old.");
            // Bug being that Java Strings do not get converted to JS strings automatically.
            // This isn't hard to work-around on the JS side, but it's easier to just
            // use the prompt bridge instead.
            return;            
        }
        SystemExposedJsApi exposedJsApi = new SystemExposedJsApi(bridge);
        webView.addJavascriptInterface(exposedJsApi, "_cordovaNative");
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在JS调用Native时，会根据_cordovaNative对象是否存在来判断使用哪种方式调用Native，SystemExposedJsApi类实现了ExposedJsApi，有以下三个方法：

    public interface ExposedJsApi {
        public String exec(int bridgeSecret, String service, String action, String callbackId, String arguments) throws JSONException, IllegalAccessException;
        public void setNativeToJsBridgeMode(int bridgeSecret, int value) throws IllegalAccessException;
        public String retrieveJsMessages(int bridgeSecret, boolean fromOnlineEvent) throws IllegalAccessException;
    }

SystemExposedJsApi如下：

    class SystemExposedJsApi implements ExposedJsApi {
        private final CordovaBridge bridge;
        SystemExposedJsApi(CordovaBridge bridge) {
            this.bridge = bridge;
        }
        @JavascriptInterface
        public String exec(int bridgeSecret, String service, String action, String callbackId, String arguments) throws JSONException, IllegalAccessException {
            return bridge.jsExec(bridgeSecret, service, action, callbackId, arguments);
        }
        @JavascriptInterface
        public void setNativeToJsBridgeMode(int bridgeSecret, int value) throws IllegalAccessException {
            bridge.jsSetNativeToJsBridgeMode(bridgeSecret, value);
        }
        @JavascriptInterface
        public String retrieveJsMessages(int bridgeSecret, boolean fromOnlineEvent) throws IllegalAccessException {
            return bridge.jsRetrieveJsMessages(bridgeSecret, fromOnlineEvent);
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;exec()方法是JS调用Native的方法。当Android版本低于4.2时，会采用prompt的方式，此方式在继承WebChromeClient的SystemWebChromeClient类中的onJsPrompt()方法中处理JS。
下面来分析Android 4.2以上版本的情况，当JS调用Native的exec方法时，会调用SystemExposedJsApi对象("_cordovaNative")的exec方法，从而调用CordovaBridge的jsExec方法，如下：

     public String jsExec(int bridgeSecret, String service, String action, String callbackId, String arguments) throws JSONException, IllegalAccessException {
        if (!verifySecret("exec()", bridgeSecret)) {
            return null;
        }
        // If the arguments weren't received, send a message back to JS.  It will switch bridge modes and try again.  See CB-2666.
        // We send a message meant specifically for this case.  It starts with "@" so no other message can be encoded into the same string.
        if (arguments == null) {
            return "@Null arguments.";
        }
        jsMessageQueue.setPaused(true);
        try {
            // Tell the resourceApi what thread the JS is running on.
            CordovaResourceApi.jsThread = Thread.currentThread();
            pluginManager.exec(service, action, callbackId, arguments);
            String ret = null;
            if (!NativeToJsMessageQueue.DISABLE_EXEC_CHAINING) {
                ret = jsMessageQueue.popAndEncode(false);
            }
            return ret;
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        } finally {
            jsMessageQueue.setPaused(false);
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;其中，又执行了PluginManager的exec(service, action, callbackId, arguments)方法，如下：

    public void exec(final String service, final String action, final String callbackId, final String rawArgs) {
        CordovaPlugin plugin = getPlugin(service);
        if (plugin == null) {
            Log.d(TAG, "exec() call to unknown plugin: " + service);
            PluginResult cr = new PluginResult(PluginResult.Status.CLASS_NOT_FOUND_EXCEPTION);
            app.sendPluginResult(cr, callbackId);
            return;
        }
        CallbackContext callbackContext = new CallbackContext(callbackId, app);
        try {
            long pluginStartTime = System.currentTimeMillis();
            boolean wasValidAction = plugin.execute(action, rawArgs, callbackContext);
            long duration = System.currentTimeMillis() - pluginStartTime;
            if (duration > SLOW_EXEC_WARNING_THRESHOLD) {
                Log.w(TAG, "THREAD WARNING: exec() call to " + service + "." + action + " blocked the main thread for " + duration + "ms. Plugin should use CordovaInterface.getThreadPool().");
            }
            if (!wasValidAction) {
                PluginResult cr = new PluginResult(PluginResult.Status.INVALID_ACTION);
                callbackContext.sendPluginResult(cr);
            }
        } catch (JSONException e) {
            PluginResult cr = new PluginResult(PluginResult.Status.JSON_EXCEPTION);
            callbackContext.sendPluginResult(cr);
        } catch (Exception e) {
            Log.e(TAG, "Uncaught exception from plugin", e);
            callbackContext.error(e.getMessage());
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在PluginManager的exec方法中，首先根据CordovaPlugin的名称得到插件，如果找不到插件，则设置错误回调信息并return。如果找到该插件，则调用：

    boolean wasValidAction = plugin.execute(action, rawArgs, callbackContext);

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;并监听返回值，返回值标识是否正确执行，如果返回false，则设置错误信息并回调：

    if (!wasValidAction) {
                PluginResult cr = new PluginResult(PluginResult.Status.INVALID_ACTION);
                callbackContext.sendPluginResult(cr);
            }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;同时，还会监控执行时间，如果时间过长，则警告用户插件执行阻塞了主线程，为了防止阻塞主线程，需要在子线程中进行耗时操作，Cordova提供了一个线程池供插件调用，在CordovaPlugin中使用以下方法获取线程时：

    cordova.getThreadPool();

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;以上为Cordova中JS调用本地插件的全部过程。