# Cordova—Android源码分析一：Cordova插件的初始化流程
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在CordovaActivity中的onCreate(Bundle savedInstanceState)中，会调用loadConfig()方法，loadConfig方法代码如下：

    protected void loadConfig() {
        ConfigXmlParser parser = new ConfigXmlParser();
        parser.parse(this);
        preferences = parser.getPreferences();
        preferences.setPreferencesBundle(getIntent().getExtras());
        // launchUrl = parser.getLaunchUrl();
        mUrl = parser.getLaunchUrl();
        pluginEntries = parser.getPluginEntries();
        Config.parser = parser;
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;使用ConfigXmlParser类来解析/res/xml/config.xml文件（ parser.parse(this) ）。在ConfigXmlParser类中，分别解析出插件的名称、全路径类名、是否立即加载等属性，通过：
    
    public void handleEndTag(XmlPullParser xml) {
        String strNode = xml.getName();
        if (strNode.equals("feature")) {
            pluginEntries.add(new PluginEntry(service, pluginClass, onload));
            service = "";
            pluginClass = "";
            insideFeature = false;
            onload = false;
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;pluginEntries.add将插件信息添加到一个ArrayList中。ConfigXmlParser中有对应pluginEntries的get方法：

    public ArrayList<PluginEntry> getPluginEntries() {
        return pluginEntries;
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;此方法在CordovaActiivty的loadConfig中，在解析完config.xml之后被调用。在调用loadUrl时，会首先判断CordovaWebView是否为空，当为null时，会调用init()方法进行初始化：

    public void loadUrl(String url) {
        if (appView == null) {
            init();
        }
        // If keepRunning
        this.keepRunning = preferences.getBoolean("KeepRunning", true);
        appView.loadUrlIntoView(url, true);
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;init()方法如下：

    protected void init() {
        appView = makeWebView();
        createViews();
        if (!appView.isInitialized()) {
            appView.init(cordovaInterface, pluginEntries, preferences);
        }
        cordovaInterface.onCordovaInit(appView.getPluginManager());
        // Wire the hardware volume controls to control media if desired.
        String volumePref = preferences.getString("DefaultVolumeStream", "");
        if ("media".equals(volumePref.toLowerCase(Locale.ENGLISH))) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在init()方法中创建CordovaWebView，同时，调用CordovaWebView的init方法，将插件信息传入。CordovaWebView是一个接口，在CordovaWebView的实现类CordovaWebViewImpl的init(CordovaInterface cordova, List<PluginEntry> pluginEntries, CordovaPreferences preferences)方法中，将pluginEntries作为参数实例化PluginManager变量：

    //....................................................................
    pluginManager = new PluginManager(this, this.cordova, pluginEntries);
    //....................................................................

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;PluginManager的构造方法如下：

    public PluginManager(CordovaWebView cordovaWebView, CordovaInterface cordova, Collection<PluginEntry> pluginEntries) {
        this.ctx = cordova;
        this.app = cordovaWebView;
        setPluginEntries(pluginEntries);
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;调用setPluginEntries方法：

    public void setPluginEntries(Collection<PluginEntry> pluginEntries) {
        if (isInitialized) {
            this.onPause(false);
            this.onDestroy();
            pluginMap.clear();
            entryMap.clear();
        }
        for (PluginEntry entry : pluginEntries) {
            addService(entry);
        }
        if (isInitialized) {
            startupPlugins();
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;开始时，isInitialized变量为false，此时，只会执行for循环方法，其中的addService方法如下：
    
    /**
     * Add a plugin class that implements a service to the service entry table.
     * This does not create the plugin object instance.
     *
     * @param entry The plugin entry
     */
    public void addService(PluginEntry entry) {
        this.entryMap.put(entry.service, entry);
        if (entry.plugin != null) {
            entry.plugin.privateInitialize(entry.service, ctx, app, app.getPreferences());
            pluginMap.put(entry.service, entry.plugin);
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;其中entry.service即为config.xml中配置的feature节点的name属性，如下：

    <feature name="NFCPlugin">
        <param name="android-package" value="com.cmbc.firefly.nfc.NFCPlugin" />
    </feature>                                                                    

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在addService方法中，首先会以Plugin的名称，即上面xml中的name作为key，存储插件信息，初始化时,entry.plugin为null，因此，不会进入下面的方法。在将插件信息添加到HashMap中后，在CordovaWebViewImpl的init方法的最后，会调用PluginManger的init()方法，如下：

    @Override
    public void init(CordovaInterface cordova, List<PluginEntry> pluginEntries, CordovaPreferences preferences) {
        if (this.cordova != null) {
            throw new IllegalStateException();
        }
        this.cordova = cordova;
        this.preferences = preferences;
        mActivity = cordova.getActivity();
        mFragment = (BaseCordovaFragment) cordova.getFragment();
        //实例化PluginManager
        pluginManager = new PluginManager(this, this.cordova, pluginEntries);
        resourceApi = new CordovaResourceApi(engine.getView().getContext(), pluginManager);
        nativeToJsMessageQueue = new NativeToJsMessageQueue();
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.NoOpBridgeMode());
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.LoadUrlBridgeMode(engine, cordova));
        if (preferences.getBoolean("DisallowOverscroll", false)) {
            engine.getView().setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        engine.init(this, cordova, engineClient, resourceApi, pluginManager, nativeToJsMessageQueue);
        // This isn't enforced by the compiler, so assert here.
        assert engine.getView() instanceof CordovaWebViewEngine.EngineView;
        pluginManager.addService(CoreAndroid.PLUGIN_NAME, "org.apache.cordova.CoreAndroid");
        pluginManager.init();
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;CordovaPlugin插件分为两种，一种是在被调用时初始化实例，一种是在网页加载时就被初始化实例，对于第二种，在init()方法中，调用startupPlugins()方法，完成创建xml配置onLoad属性为true的插件。

     /**
     * Create plugins objects that have onload set.
     */
    private void startupPlugins() {
        for (PluginEntry entry : entryMap.values()) {
            // Add a null entry to for each non-startup plugin to avoid
            // ConcurrentModificationException
            // When iterating plugins.
            if (entry.onload) {
                getPlugin(entry.service);
            } else {
                pluginMap.put(entry.service, null);
            }
        }
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;其中的getPlugin方法如下：

    /**
     * Get the plugin object that implements the service.
     * If the plugin object does not already exist, then create it.
     * If the service doesn't exist, then return null.
     *
     * @param service       The name of the service.
     * @return              CordovaPlugin or null
     */
    public CordovaPlugin getPlugin(String service) {
        CordovaPlugin ret = pluginMap.get(service);
        if (ret == null) {
            PluginEntry pe = entryMap.get(service);
            if (pe == null) {
                return null;
            }
            if (pe.plugin != null) {
                ret = pe.plugin;
            } else {
                ret = instantiatePlugin(pe.pluginClass);
            }
            ret.privateInitialize(service, ctx, app, app.getPreferences());
            pluginMap.put(service, ret);
        }
        return ret;
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;流程如下：

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(1).首先在以插件名称为key，以插件实例为value的LinkedHashMap<String, CordovaPlugin>中查找插件是否已经被实例化，如果被实例化则直接返回；

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(2).若map中没有service对应的CordovaPlugin示实例，则在以插件名称为key，以插件的相关信息为value的LinkedHashMap<String, PluginEntry>中查找插件信息，如果对应的此信息没有查找到，则说明config.xml中没有配置此插件信息，则返回null。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(3).如果从LinkedHashMap<String, PluginEntry>查找到的PluginEntry不为null，则判断下PluginEntry中的CordovaPlugin是否为null，如果为null，则调用instantiatePlugin方法得到CordovaPlugin的实例，此方法根据插件的全路径类名反射得到插件实例。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(4).在实例化CordovaPlugin后，调用CordovaPlugin的privateInitialize进行初始化操作。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(5).将实例化后的CordovaPlugin添加到LinkedHashMap<String, CordovaPlugin>中，并返回CordovaPluign实例。

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;CordovaPlugin的privateInitialize方法如下：

    /**
     * Call this after constructing to initialize the plugin.
     * Final because we want to be able to change args without breaking plugins.
     */
    public final void privateInitialize(String serviceName, CordovaInterface cordova, CordovaWebView webView, CordovaPreferences preferences) {
        assert this.cordova == null;
        this.serviceName = serviceName;
        this.cordova = cordova;
        this.webView = webView;
        this.preferences = preferences;
        initialize(cordova, webView);
        pluginInitialize();
    }

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在privateInitialize的方法最后，会调用initialize方法和pluginInitialize方法，用户自定义CordovaPlugin时，可以复写这两个方法完成一些插件的初始化操作。
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;此时，完成了整个插件的初始化过程。