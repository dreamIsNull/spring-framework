> 参考网址：<http://cmsblogs.com/?p=2731>

#### 目录

* [](#)

****

&nbsp;&nbsp; [【【Spring 5.0.x】—— 6. IoC 之注册 BeanDefinitions]() 中分析到，`Spring` 中有两种解析 `Bean` 的方式：

* 如果根节点或者子节点采用默认命名空间的话，则调用 `#parseDefaultElement(...)` 方法，进行**默认**标签解析
* 否则，调用 `BeanDefinitionParserDelegate#parseCustomElement(...)` 方法，进行**自定义**解析。

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

public static final String IMPORT_ELEMENT = "import";
public static final String ALIAS_ATTRIBUTE = "alias";
public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;
public static final String NESTED_BEANS_ELEMENT = "beans";

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

&nbsp;&nbsp; 默认标签的解析是在 `#parseDefaultElement(...)`方法中进行的，分别对4种不同的标签(  `import`、`alias`、`bean`、`beans` )做了不同的处理。下面我们从默认标签的`bean`标签开始。

# 1. processBeanDefinition

 &nbsp;&nbsp; 在方法 `#parseDefaultElement(...)` 方法中，如果遇到标签为 `bean` 时，则调用 `#processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate)` 方法，进行 `bean` 标签的解析。 

```java
// org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader.java

/**
 * 进行 bean 标签解析
 */
protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
	/*
	 * <1> 委托BeanDefinitionParserDelegate进行解析
	 * 如果解析成功，则返回 BeanDefinitionHolder 对象。BeanDefinitionHolder 包含了配置文件配置的各种属性（id,name,class,alias）
	 * 如果解析失败，则返回 null
	 */
	BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
	if (bdHolder != null) {
		// <2> 进行自定义标签处理
		bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
		try {
			// <3> 进行 BeanDefinition 的注册
			BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
		}
		catch (BeanDefinitionStoreException ex) {
			getReaderContext().error("Failed to register bean definition with name '" +
					bdHolder.getBeanName() + "'", ele, ex);
		}
		// <4> 发出响应事件，通知相关的监听器，已完成该 Bean 标签的解析。
		getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
	}
}
```

整个过程分为四个步骤：

1. 调用`BeanDefinitionParserDelegate#parseBeanDefinitionElement(Element ele, BeanDefinitionParserDelegate delegate)`方法，进行元素解析。

   * 如果解析**失败**，则返回 `null`，错误由 `ProblemReporter` 处理
   * 如果解析**成功**，则返回 `BeanDefinitionHolder` 实例 `bdHolder` 。`BeanDefinitionHolder` **包含了配置文件配置的各种属性（`id`,`name`,`class`,`alias`）**
   * 详细解析，见 [「2. parseBeanDefinitionElement」](#2) 

2. 若实例 `bdHolder` 不为空，则调用 `BeanDefinitionParserDelegate#decorateBeanDefinitionIfRequired(Element ele, BeanDefinitionHolder bdHolder)` 方法，进行**自定义标签处理**。

3. 解析完成后，则调用 `BeanDefinitionReaderUtils#registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)` 方法，对 `bdHolder` 进行 `BeanDefinition` 的注册。

4. 发出响应事件，通知相关的监听器，完成 Bean 标签解析。

# 2. parseBeanDefinitionElement

 &nbsp;&nbsp; `BeanDefinitionParserDelegate#parseBeanDefinitionElement(Element ele, BeanDefinitionParserDelegate delegate)` 方法，进行 `<bean>` 元素解析 

```java
// org.springframework.beans.factory.xml.BeanDefinitionParserDelegate.java

@Nullable
public BeanDefinitionHolder parseBeanDefinitionElement(Element ele) {
	return parseBeanDefinitionElement(ele, null);
}

@Nullable
public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {

	// <1> 解析 id 和 name 属性
	String id = ele.getAttribute(ID_ATTRIBUTE);
	String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

	// <1> 计算别名集合
	List<String> aliases = new ArrayList<>();
	if (StringUtils.hasLength(nameAttr)) {
		String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
		aliases.addAll(Arrays.asList(nameArr));
	}

	// <3.1> beanName ，优先，使用 id
	String beanName = id;

	// <3.2> beanName ，其次，使用 aliases 的第一个
	if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
		beanName = aliases.remove(0);
		if (logger.isDebugEnabled()) {
			logger.debug("No XML 'id' specified - using '" + beanName +
					"' as bean name and " + aliases + " as aliases");
		}
	}

	if (containingBean == null) {
		// <2> 检查 beanName 的唯一性
		checkNameUniqueness(beanName, aliases, ele);
	}

	// <4> 解析属性，构造 AbstractBeanDefinition 对象
	AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
	if (beanDefinition != null) {
		// <3.3> beanName ，再次，使用 beanName 生成规则
		if (!StringUtils.hasText(beanName)) {
			try {
				if (containingBean != null) {
					// <3.3> 生成唯一的 beanName
					beanName = BeanDefinitionReaderUtils.generateBeanName(
							beanDefinition, this.readerContext.getRegistry(), true);
				}
				else {
					// <3.3> 生成唯一的 beanName
					beanName = this.readerContext.generateBeanName(beanDefinition);

					String beanClassName = beanDefinition.getBeanClassName();
					if (beanClassName != null &&
							beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
							!this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
						aliases.add(beanClassName);
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Neither XML 'id' nor 'name' specified - " +
							"using generated bean name [" + beanName + "]");
				}
			}
			catch (Exception ex) {
				error(ex.getMessage(), ele);
				return null;
			}
		}
		String[] aliasesArray = StringUtils.toStringArray(aliases);

		// <5> 创建 BeanDefinitionHolder 对象
		return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
	}

	return null;
}
```

 &nbsp;&nbsp; 这里还没有对 `bean` 标签进行解析，只是在解析动作之前做了一些功能架构，主要的工作有：

*  `<1>` 处，解析 `id`、`name` 属性，确定 `aliases` 集合 

*  `<2>` 处，检测 `beanName` 是否唯一

  ```java
  // org.springframework.beans.factory.xml.BeanDefinitionParserDelegate.java
  
  /**
   * 已使用 Bean 名字的集合
   */
  private final Set<String> usedNames = new HashSet<>();
  
  /**
   * 检测 beanName 是否唯一
   */
  protected void checkNameUniqueness(String beanName, List<String> aliases, Element beanElement) {
  	String foundName = null;
  
  	if (StringUtils.hasText(beanName) && this.usedNames.contains(beanName)) {
  		foundName = beanName;
  	}
  	if (foundName == null) {
  		foundName = CollectionUtils.findFirstMatch(this.usedNames, aliases);
  	}
  	// 若已使用，使用 problemReporter 提示错误
  	if (foundName != null) {
  		error("Bean name '" + foundName + "' is already used in this <beans> element", beanElement);
  	}
  
  	// 添加到 usedNames 集合
  	this.usedNames.add(beanName);
  	this.usedNames.addAll(aliases);
  }
  ```

  &nbsp;&nbsp;  这里有必要说下 `beanName` 的命名规则 

  * `<3.1>` 处，如果 `id` 不为空，则 `beanName = id` 。
  * `<3.2>` 处，如果 `id` 为空，但是 `aliases` 不空，则 `beanName` 为 `aliases` 的**第一个**元素
  * `<3.3>` 处，如果两者都为空，则根据**默认规则**来设置 `beanName` 。

*  `<4>` 处，调用 `#parseBeanDefinitionElement(Element ele, String beanName, BeanDefinition containingBean)` 方法，对属性进行解析并封装成 `AbstractBeanDefinition` 实例 `beanDefinition` 。见 [「2.1 parseBeanDefinitionElement」](#2.1) 。 

*  `<5>` 处，根据所获取的信息（`beanName`、`aliases`、`beanDefinition`）构造 `BeanDefinitionHolder` 实例对象并返回。其中，`BeanDefinitionHolder` 的简化代码如下： 

  ```java
  // org.springframework.beans.factory.config.BeanDefinitionHolder.java
  
  /**
   * BeanDefinition 对象
   */
  private final BeanDefinition beanDefinition;
  
  /**
   * Bean 名字
   */
  private final String beanName;
  
  /**
   * 别名集合
   */
  @Nullable
  private final String[] aliases;
  ```

## 2.1 parseBeanDefinitionElement

 &nbsp;&nbsp; `#parseBeanDefinitionElement(Element ele, String beanName, BeanDefinition containingBean)` 方法，对属性进行解析并封装成 `AbstractBeanDefinition` 实例

```java
// org.springframework.beans.factory.xml.BeanDefinitionParserDelegate.java

@Nullable
public AbstractBeanDefinition parseBeanDefinitionElement(
		Element ele, String beanName, @Nullable BeanDefinition containingBean) {

	this.parseState.push(new BeanEntry(beanName));

	// 解析 class 属性
	String className = null;
	if (ele.hasAttribute(CLASS_ATTRIBUTE)) {
		className = ele.getAttribute(CLASS_ATTRIBUTE).trim();
	}

	// 解析 parent 属性
	String parent = null;
	if (ele.hasAttribute(PARENT_ATTRIBUTE)) {
		parent = ele.getAttribute(PARENT_ATTRIBUTE);
	}

	try {
		// 创建用于承载属性的 AbstractBeanDefinition 实例
		AbstractBeanDefinition bd = createBeanDefinition(className, parent);

		// 解析默认 bean 的各种属性
		parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
		bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));

		/*
		 * 下面是解析 <bean>......</bean> 内部的子元素，
    	 * 解析出来以后的信息都放到 bd 的属性中
		 */

		// 解析元数据 <meta />
		parseMetaElements(ele, bd);
		// 解析 lookup-method 属性 <lookup-method />
		parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
		// 解析 replaced-method 属性 <replaced-method />
		parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

		// 解析构造函数参数 <constructor-arg />
		parseConstructorArgElements(ele, bd);
		// 解析 property 子元素 <property />
		parsePropertyElements(ele, bd);
		// 解析 qualifier 子元素 <qualifier />
		parseQualifierElements(ele, bd);

		bd.setResource(this.readerContext.getResource());
		bd.setSource(extractSource(ele));

		return bd;
	}
	catch (ClassNotFoundException ex) {
		error("Bean class [" + className + "] not found", ele, ex);
	}
	catch (NoClassDefFoundError err) {
		error("Class that bean class [" + className + "] depends on not found", ele, err);
	}
	catch (Throwable ex) {
		error("Unexpected failure during bean definition parsing", ele, ex);
	}
	finally {
		this.parseState.pop();
	}

	return null;
}
```

 &nbsp;&nbsp; 至此，`bean` 标签的所有属性我们都可以看到其解析的过程，也就说到这里我们已经解析一个基本可用的 `BeanDefinition` 。 

## 2.2 createBeanDefinition

&nbsp;&nbsp;  `#createBeanDefinition(String className, String parentName)` 方法，创建 `AbstractBeanDefinition` 对象。 

```java
// org.springframework.beans.factory.xml.BeanDefinitionParserDelegate.java

protected AbstractBeanDefinition createBeanDefinition(@Nullable String className, @Nullable String parentName)
		throws ClassNotFoundException {

	return BeanDefinitionReaderUtils.createBeanDefinition(
			parentName, className, this.readerContext.getBeanClassLoader());
}
```

