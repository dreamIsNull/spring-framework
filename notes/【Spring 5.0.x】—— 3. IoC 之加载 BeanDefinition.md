> 参考网址：<http://cmsblogs.com/?p=2658>



#### 目录

* [1. 容器初始化过程](#1)
* [2. loadBeanDefinitions](#2)
* [3. doLoadBeanDefinitions](#3)
  * [3.1 doLoadDocument](#3.1)
  * [3.2 registerBeanDefinitions](#3.2)



****
<span id="1"></span>

# 1. 容器初始化过程

```java
ClassPathResource resource = new ClassPathResource("bean.xml"); // <1>
DefaultListableBeanFactory factory = new DefaultListableBeanFactory(); // <2>
XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory); // <3>
reader.loadBeanDefinitions(resource); // <4>
```

&nbsp;&nbsp; 上面这段代码是 `Spring` 中**编程式使用 `IoC` 容器**，通过这四段简单的代码，我们可以初步判断 `IoC` 容器的使用过程。 

1. 获取资源
2. 获取 `BeanFactory`
3. 根据新建的 `BeanFactory` 创建一个 `BeanDefinitionReader` 对象，该 `Reader` 对象为资源的**解析器**
4. 装载资源

&nbsp;&nbsp;  整个过程就分为三个步骤：**资源定位、装载、注册**

.<center>![Ioc 初始化过程](source/Ioc 初始化过程.png)</center>

* **资源定位**。我们一般用外部资源来描述 `Bean` 对象，所以在初始化 `IoC` 容器的第一步就是需要定位这个外部资源。`Spring`使用`Resource`和`ResourceLoader`来进行资源定位与加载。（见[【Spring 5.0.x】—— 2. IoC 之 Spring 统一资源加载策略]()）
* **装载**。装载就是 `BeanDefinition` 的载入。`BeanDefinitionReader` 读取、解析 `Resource` 资源，也就是将用户定义的 `Bean` 表示成 `IoC` 容器的内部数据结构：`BeanDefinition` 。
  *  在 `IoC` 容器内部维护着一个 **`BeanDefinition` Map** 的数据结构
  *  在配置文件中每一个`<bean>`都对应着一个 `BeanDefinition` 对象

> `BeanDefinitionReader` ，主要定义资源文件读取并转换为 `BeanDefinition` 的各个功能。

* **注册**。 向 `IoC`容器注册在第二步解析好的 `BeanDefinition`，这个过程是通过 `BeanDefinitionRegistry` 接口来实现的。在 `IoC`容器内部其实是将第二个过程解析得到的 `BeanDefinition` 注入到一个 `HashMap` 容器中，`IoC` 容器就是通过这个 `HashMap` 来维护这些 `BeanDefinition` 的。 
  *  在这里需要注意的一点是这个过程**并没有完成依赖注入（`Bean` 创建）**，`Bean` 创建是发生在应用第一次调用 `#getBean(...)` 方法，向容器索要 `Bean` 时。
  *  当然我们可以通过设置预处理，即对某个 `Bean` 设置 `lazyinit = false` 属性，那么这个 `Bean` 的**依赖注入就会在容器初始化的时候完成**。  

> 上述步骤 ：**XML Resource => XML Document => Bean Definition** 。

<span id="2"></span>

# 2. loadBeanDefinitions

&nbsp;&nbsp; **资源定位与加载**在[【Spring 5.0.x】—— 2. IoC 之 Spring 统一资源加载策略]()已经分析过了。这里我们分析**加载**，上面看到的 `reader.loadBeanDefinitions(resource)` 代码，才是**加载资源的真正实现**，所以我们直接从该方法入手。代码如下： 

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

@Override
public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
	return loadBeanDefinitions(new EncodedResource(resource));
}
```

- 从指定的 `xml` 文件加载 `Bean Definition` ，这里会先对 `Resource` 资源封装成 `org.springframework.core.io.support.EncodedResource` 对象。这里为什么需要将 `Resource` 封装成 `EncodedResource` 呢？主要是为了对 `Resource` 进行编码，保证内容读取的正确性。

```java
// org.springframework.core.io.support.EncodedResource.java

/**
 * 获取编码后的流文件
 */
public Reader getReader() throws IOException {
	// 如果指定了编码，则构建指定编码的流文件
	if (this.charset != null) {
		return new InputStreamReader(this.resource.getInputStream(), this.charset);
	}
	else if (this.encoding != null) {
		return new InputStreamReader(this.resource.getInputStream(), this.encoding);
	}
	else {
		return new InputStreamReader(this.resource.getInputStream());
	}
}
```



- 然后，再调用 `#loadBeanDefinitions(EncodedResource encodedResource)` 方法，执行**真正的逻辑实现**。

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

/**
 * 当前线程，正在加载的 EncodedResource 集合。
 */
private final ThreadLocal<Set<EncodedResource>> resourcesCurrentlyBeingLoaded = new NamedThreadLocal<>("XML bean definition resources currently being loaded");

public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
	Assert.notNull(encodedResource, "EncodedResource must not be null");
	if (logger.isInfoEnabled()) {
		logger.info("Loading XML bean definitions from " + encodedResource);
	}

	// <1> 获取已经加载过的资源
	Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
	if (currentResources == null) {
		currentResources = new HashSet<>(4);
		this.resourcesCurrentlyBeingLoaded.set(currentResources);
	}
	
	// 将当前资源加入记录中。如果已存在，抛出异常
	if (!currentResources.add(encodedResource)) {
		throw new BeanDefinitionStoreException(
				"Detected cyclic loading of " + encodedResource + " - check your import definitions!");
	}
	try {
		// <2> 从 EncodedResource 获取封装的 Resource ，并从 Resource 中获取其中的 InputStream
		InputStream inputStream = encodedResource.getResource().getInputStream();
		try {
            // InputSource这个类不是来自Spring,它的全路径是org.xml.sax.InputSource
			InputSource inputSource = new InputSource(inputStream);
			if (encodedResource.getEncoding() != null) {
				// 设置编码
				inputSource.setEncoding(encodedResource.getEncoding());
			}
			// 核心逻辑部分，执行加载 BeanDefinition
			return doLoadBeanDefinitions(inputSource, encodedResource.getResource());
		}
		finally {
            // 关闭输入流
			inputStream.close();
		}
	}
	catch (IOException ex) {
		throw new BeanDefinitionStoreException(
				"IOException parsing XML document from " + encodedResource.getResource(), ex);
	}
	finally {
		// <3> 从缓存中剔除该资源 
		currentResources.remove(encodedResource);
		if (currentResources.isEmpty()) {
			this.resourcesCurrentlyBeingLoaded.remove();
		}
	}
}
```

* `<1>` 处通过 `this.resourcesCurrentlyBeingLoaded.get()` 代码，来**获取已经加载过的资源**，然后将 `encodedResource` 加入其中，如果  `resourcesCurrentlyBeingLoaded`  中已经存在该资源，则抛出 `BeanDefinitionStoreException` 异常。
* 为什么需要这么做呢？答案在 `"Detected cyclic loading"` ，避免一个 `EncodedResource` 在加载时，还没加载完成，又加载自身，从而导致**死循环**。
  * 所以，在 `<3>` 处，当一个 `EncodedResource` 加载完成后，需要从缓存中剔除。
* `<2>` 处理，从 `encodedResource` 获取封装的 `Resource` 资源，并从 `Resource` 中获取相应的 `InputStream` ，然后将 `InputStream` 封装为 `InputSource` ，最后调用 `#doLoadBeanDefinitions(InputSource inputSource, Resource resource)` 方法，执行加载 `Bean Definition` 的真正逻辑。

<span id="3"></span>
# 3. doLoadBeanDefinitions

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
		throws BeanDefinitionStoreException {
	try {
		// <1> 获取 XML Document 实例
		Document doc = doLoadDocument(inputSource, resource);
		// <2> 根据 Document 实例，注册 BeanDefinition 信息
		return registerBeanDefinitions(doc, resource);
	}
	catch (BeanDefinitionStoreException ex) {
		throw ex;
	}
	catch (SAXParseException ex) {
		throw new XmlBeanDefinitionStoreException(resource.getDescription(),
				"Line " + ex.getLineNumber() + " in XML document from " + resource + " is invalid", ex);
	}
	catch (SAXException ex) {
		throw new XmlBeanDefinitionStoreException(resource.getDescription(),
				"XML document from " + resource + " is invalid", ex);
	}
	catch (ParserConfigurationException ex) {
		throw new BeanDefinitionStoreException(resource.getDescription(),
				"Parser configuration exception parsing XML from " + resource, ex);
	}
	catch (IOException ex) {
		throw new BeanDefinitionStoreException(resource.getDescription(),
				"IOException parsing XML document from " + resource, ex);
	}
	catch (Throwable ex) {
		throw new BeanDefinitionStoreException(resource.getDescription(),
				"Unexpected exception parsing XML document from " + resource, ex);
	}
}
```

* 在 `<1>` 处，调用 `#doLoadDocument(InputSource inputSource, Resource resource)` 方法，根据 `xml` 文件，获取 `Document` 实例。

* 在 `<2>` 处，调用 `#registerBeanDefinitions(Document doc, Resource resource)` 方法，根据获取的 `Document` 实例，注册 `BeanDefinition`信息。

<span id="3.1"></span>
## 3.1 doLoadDocument

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

/**
 * 获取 XML Document 实例
 */
protected Document doLoadDocument(InputSource inputSource, Resource resource) throws Exception {
    // #getValidationModeForResource()获取指定资源(xml)的验证模式
	return this.documentLoader.loadDocument(inputSource, getEntityResolver(), this.errorHandler,
			getValidationModeForResource(resource), isNamespaceAware());
}
```

* 调用 `#getValidationModeForResource(Resource resource)` 方法，获取指定资源（`xml`）的**验证模式**。见 [【Spring 5.0.x】—— 4. IoC 之获取验证模型]() 。
* 调用 `DocumentLoader#loadDocument(InputSource inputSource, EntityResolver entityResolver, ErrorHandler errorHandler, int validationMode, boolean namespaceAware)` 方法，获取 `XML Document` 实例。见 [【Spring 5.0.x】—— 5. IoC 之获取 Document 对象]() 。

<span id="3.2"></span>
## 3.2 registerBeanDefinitions

&nbsp;&nbsp;  这个方法将`XmlBeanDefinitionReader#doLoadDocument()`返回的`Documen`示例解析为`BeanDefinitions`并注册到`IoC`容器。见[【Spring 5.0.x】—— 6. IoC 之注册 BeanDefinitions]()

