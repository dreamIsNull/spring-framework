> 参考网址：<http://cmsblogs.com/?p=2697>

#### 目录

* [1. createBeanDefinitionDocumentReader](#1)
* [2. registerBeanDefinitions](#2)
  * [2.1 DefaultBeanDefinitionDocumentReader](#2.1)
    * [2.1.1 parseBeanDefinitions](#2.1.1)
* [3. createReaderContext](#3)
* [4. 总结](#4)

****

 &nbsp;&nbsp; 获取 `XML Document` 对象后，会根据**该对象(`XML Document`)和 `Resource` 资源**对象调用 `XmlBeanDefinitionReader#registerBeanDefinitions(Document doc, Resource resource)` 方法，开始注册 `BeanDefinitions` 之旅 。

```java
// org.springframework.beans.factory.support.AbstractBeanDefinitionReader.java

private final BeanDefinitionRegistry registry;


// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

/**
 * 注册 BeanDefinitions
 */
public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
	// <1> 使用 DefaultBeanDefinitionDocumentReader 创建 BeanDefinitionDocumentReader 对象
	BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
	// <2> 获取已注册的 BeanDefinition 数量
	int countBefore = getRegistry().getBeanDefinitionCount();
	// <3> 创建 XmlReaderContext 对象(#createReaderContext(resource))
	// <4> 注册 BeanDefinition
	documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
	// <5> 计算新注册的 BeanDefinition 数量
	return getRegistry().getBeanDefinitionCount() - countBefore;
}
```

*  `<1>` 处，调用 `#createBeanDefinitionDocumentReader()` 方法，实例化 `BeanDefinitionDocumentReader` 对象

  >  `BeanDefinitionDocumentReader` 定义读取 `Document` 并注册 `BeanDefinition` 功能   

*  `<2>` 处，调用 `BeanDefinitionRegistry#getBeanDefinitionCount()` 方法，获取**已注册**的 `BeanDefinition` 数量
*  `<3>` 处，调用 `#createReaderContext(Resource resource)` 方法，创建 `XmlReaderContext` 对象  
*  `<4>` 处，调用 `BeanDefinitionDocumentReader#registerBeanDefinitions(Document doc, XmlReaderContext readerContext)` 方法，读取 `XML` 元素，注册 `BeanDefinition`  
*  `<5>` 处，计算**新注册**的 `BeanDefinition` 数量

<span id="1"></span>
# 1. createBeanDefinitionDocumentReader

 &nbsp;&nbsp; `#createBeanDefinitionDocumentReader()`，实例化 `BeanDefinitionDocumentReader` 对象。

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

/**
 * documentReader 的类
 */
private Class<? extends BeanDefinitionDocumentReader> documentReaderClass = DefaultBeanDefinitionDocumentReader.class;

protected BeanDefinitionDocumentReader createBeanDefinitionDocumentReader() {
	return BeanUtils.instantiateClass(this.documentReaderClass);
}
```

&nbsp;&nbsp;  `documentReaderClass` 的默认值为 `DefaultBeanDefinitionDocumentReader.class` 。详细解析键见[「2.1 DefaultBeanDefinitionDocumentReader」](#2.1)

<span id="2"></span>
# 2. registerBeanDefinitions

&nbsp;&nbsp; `BeanDefinitionDocumentReader#registerBeanDefinitions(Document doc, XmlReaderContext readerContext)` 方法，注册 `BeanDefinition` ，在接口 `BeanDefinitionDocumentReader` 中定义 。

```java
// org.springframework.beans.factory.xml.BeanDefinitionDocumentReader.java

public interface BeanDefinitionDocumentReader {

	void registerBeanDefinitions(Document doc, XmlReaderContext readerContext)
			throws BeanDefinitionStoreException;

}
```

 &nbsp;&nbsp; **从给定的 `Document` 对象中解析定义的 `BeanDefinition` 并将他们注册到注册表中**。方法接收两个参数： 

*  `doc` 方法参数：待解析的 `Document` 对象。 
*  `readerContext` 方法，**解析器的当前上下文，包括目标注册表和被解析的资源**。它是根据 `Resource` 来创建的，见 [「3. createReaderContext」](#3) 。 

<span id="2.1"></span>
## 2.1 DefaultBeanDefinitionDocumentReader

 &nbsp;&nbsp; `BeanDefinitionDocumentReader` 有且只有一个默认实现类 `DefaultBeanDefinitionDocumentReader` 。它对 `#registerBeanDefinitions(...)` 方法的实现代码如下 

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

@Nullable
private XmlReaderContext readerContext;

@Nullable
private BeanDefinitionParserDelegate delegate;

@Override
public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
	this.readerContext = readerContext;
	logger.debug("Loading bean definitions");
	// 获得 XML Document Root Element
	Element root = doc.getDocumentElement();
	// 执行注册 BeanDefinition
	doRegisterBeanDefinitions(root);
}

/**
 * 注册BeanDefinition
 */
protected void doRegisterBeanDefinitions(Element root) {
	// 记录老的 BeanDefinitionParserDelegate 对象
	BeanDefinitionParserDelegate parent = this.delegate;
	// <1> 创建 BeanDefinitionParserDelegate 对象，并进行设置到 delegate
	this.delegate = createDelegate(getReaderContext(), root, parent);

	// <2> 检查 <beans /> 根标签的命名空间是否为空，或者是 http://www.springframework.org/schema/beans
	if (this.delegate.isDefaultNamespace(root)) {
		// <2.1> 处理 profile 属性。
		String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
		if (StringUtils.hasText(profileSpec)) {
			// <2.2> 使用分隔符切分，可能有多个 profile
			String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
					profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
			// <2.3> 如果所有 profile 都无效，则不进行注册
			if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
				if (logger.isInfoEnabled()) {
					logger.info("Skipped XML bean definition file due to specified profiles [" + profileSpec +
							"] not matching: " + getReaderContext().getResource());
				}
				return;
			}
		}
	}
	// <3> 解析前处理
	preProcessXml(root);
	// <4> 解析
	parseBeanDefinitions(root, this.delegate);
	// <5> 解析后处理
	postProcessXml(root);

	// 设置 delegate 回老的 BeanDefinitionParserDelegate 对象
	this.delegate = parent;
}
```

*  `<1>` 处，创建 `BeanDefinitionParserDelegate` 对象，并进行设置到 `this.delegate` 。`BeanDefinitionParserDelegate` 是一个重要的类，它负责**解析 BeanDefinition**。 

  > `BeanDefinitionParserDelegate`定义解析`XML Element`的各种方法

  ```java
  // org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java
  
  protected BeanDefinitionParserDelegate createDelegate(
  		XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {
  
  	// 创建 BeanDefinitionParserDelegate 对象
  	BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
  	// 初始化默认
  	delegate.initDefaults(root, parentDelegate);
  	return delegate;
  }
  ```

*  `<2>` 处，检查 `<beans />` **根**标签的命名空间是否为空，或者是 http://www.springframework.org/schema/beans 。 

  * `<2.1>` 处，判断 `<beans />` 上是否配置了 `profile` 属性，`profile`属性用来分隔不同环境的配置，比如开发、测试。
  *  `<2.2>` 处，使用分隔符切分，可能有**多个** profile 。
  *  `<2.3>` 处，判断，如果所有 `profile` 都无效，则 `return` 不进行注册。

* `<4>` 处，调用 `#parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate)` 方法，进行**解析**逻辑。详细解析，见 [「3.1 parseBeanDefinitions」](#3.1) 。 

*  `<3>` / `<5>` 处，解析**前后**的处理，目前这两个方法都是空实现，交由子类来实现。 

  ```java
  // org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java
  
  protected void preProcessXml(Element root) {}
  
  protected void postProcessXml(Element root) {}
  ```

<span id="2.1.1"></span>
### 2.1.1 parseBeanDefinitions

 &nbsp;&nbsp; `#parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate)` 方法，进行解析逻辑。 

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

/**
 * Parse the elements at the root level in the document:
 * "import", "alias", "bean".
 * @param root the DOM root element of the document
 */
protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
	// <1> 如果根节点使用默认命名空间，执行默认解析
	if (delegate.isDefaultNamespace(root)) {
		// 遍历子节点
		NodeList nl = root.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				Element ele = (Element) node;
				if (delegate.isDefaultNamespace(ele)) {
					// <1> 如果该节点使用默认命名空间，执行默认解析
					parseDefaultElement(ele, delegate);
				}
				else {
					// 如果该节点非默认命名空间，执行自定义解析
					delegate.parseCustomElement(ele);
				}
			}
		}
	}
	else {
		// <2> 如果根节点非默认命名空间，执行自定义解析
		delegate.parseCustomElement(root);
	}
}
```

* `Spring` 有**两种** `Bean` 声明方式：

	*  配置文件式声明：`<bean id="person" name="per" class="com.test.Person" />` 。对应 `<1>` 处。 
	*  自定义注解方式：`<tx:annotation-driven/>` 。对应 `<2>` 处。 
*  `<1>` 处，如果**根**节点或**子**节点**使用**默认命名空间，调用 `#parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate)` 方法，执行默认解析。 

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
	if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
		// 对 <import> 标签进行处理
		importBeanDefinitionResource(ele);
	}
	else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
		// 对 <alias> 标签进行处理
		processAliasRegistration(ele);
	}
	else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
		// 对 <bean> 标签进行处理
		processBeanDefinition(ele, delegate);
	}
	else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
		// 对 <beans> 标签进行处理
		doRegisterBeanDefinitions(ele);
	}
}
```

*  `<2>` 处，如果**根**节点或**子**节点**不使用**默认命名空间，调用 `BeanDefinitionParserDelegate#parseCustomElement(Element ele)` 方法，执行**自定义**解析。 

<span id="3"></span>
# 3. createReaderContext

&nbsp;&nbsp;  `#createReaderContext(Resource resource)` 方法，创建 `XmlReaderContext` 对象。 

```java
// org.springframework.beans.factory.xml.XmlBeanDefinitionReader.java

private ProblemReporter problemReporter = new FailFastProblemReporter();

private ReaderEventListener eventListener = new EmptyReaderEventListener();

private SourceExtractor sourceExtractor = new NullSourceExtractor();

@Nullable
private NamespaceHandlerResolver namespaceHandlerResolver;

/**
 * Create the {@link XmlReaderContext} to pass over to the document reader.
 */
public XmlReaderContext createReaderContext(Resource resource) {
	return new XmlReaderContext(resource, this.problemReporter, this.eventListener,
			this.sourceExtractor, this, getNamespaceHandlerResolver());
}
```

<span id="4"></span>

# 4. 总结

&nbsp;&nbsp; `XmlBeanDefinitionReader#doLoadBeanDefinitions(InputSource inputSource, Resource resource)` 方法中，做的三件事情已经全部分析完毕，下面将对 **BeanDefinition 的解析过程**做详细分析说明。

&nbsp;&nbsp; `XmlBeanDefinitionReader#doLoadBeanDefinitions(InputSource inputSource, Resource resource)` 方法，整体时序图如下：

.<center>![doLoadBeanDefinitions 时序图](source/doLoadBeanDefinitions 时序图.png)</center>

   &nbsp;&nbsp; 红框部分，就是 **BeanDefinition 的解析过程**。 

