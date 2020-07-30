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

&nbsp;&nbsp;  委托 `BeanDefinitionReaderUtils` 创建

```java
// org.springframework.beans.factory.support.BeanDefinitionReaderUtils.java

public static AbstractBeanDefinition createBeanDefinition(
		@Nullable String parentName, @Nullable String className, @Nullable ClassLoader classLoader) throws ClassNotFoundException {

	// 创建 GenericBeanDefinition 对象
	GenericBeanDefinition bd = new GenericBeanDefinition();

	// 设置 parentName
	bd.setParentName(parentName);
	if (className != null) {
		if (classLoader != null) {
			// 设置 beanClass
			bd.setBeanClass(ClassUtils.forName(className, classLoader));
		}
		else {
			// 设置 beanClassName
			bd.setBeanClassName(className);
		}
	}
	return bd;
}
```

&nbsp;&nbsp;  该方法主要是，创建 `GenericBeanDefinition` 对象，并设置 `parentName`、`className`、`beanClass` 属性。 

# 3. BeanDefinition

 `org.springframework.beans.factory.config.BeanDefinition `，是一个接口， 它描述了一个 `Bean` 实例的**定义**，包括**属性值、构造方法值和继承自它的类的更多信息** 。

```java
// org.springframework.beans.factory.config.BeanDefinition.java

/**
 * 单例 singleton
 */
String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

/**
 * 原型 prototype
 */
String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;


int ROLE_APPLICATION = 0;
int ROLE_SUPPORT = 1;
int ROLE_INFRASTRUCTURE = 2;

void setParentName(@Nullable String parentName);
@Nullable
String getParentName();

void setBeanClassName(@Nullable String beanClassName);
@Nullable
String getBeanClassName();

void setScope(@Nullable String scope);
@Nullable
String getScope();

void setLazyInit(boolean lazyInit);
boolean isLazyInit();

void setDependsOn(@Nullable String... dependsOn);
@Nullable
String[] getDependsOn();

void setAutowireCandidate(boolean autowireCandidate);
boolean isAutowireCandidate();

void setPrimary(boolean primary);
boolean isPrimary();

void setFactoryBeanName(@Nullable String factoryBeanName);
@Nullable
String getFactoryBeanName();

void setFactoryMethodName(@Nullable String factoryMethodName);
@Nullable
String getFactoryMethodName();

ConstructorArgumentValues getConstructorArgumentValues();
default boolean hasConstructorArgumentValues() {
	return !getConstructorArgumentValues().isEmpty();
}

MutablePropertyValues getPropertyValues();
default boolean hasPropertyValues() {
	return !getPropertyValues().isEmpty();
}

void setInitMethodName(@Nullable String initMethodName);
@Nullable
String getInitMethodName();

void setDestroyMethodName(@Nullable String destroyMethodName);
@Nullable
String getDestroyMethodName();

void setRole(int role);
int getRole();

void setDescription(@Nullable String description);
@Nullable
String getDescription();

boolean isSingleton();

boolean isPrototype();

boolean isAbstract();

@Nullable
String getResourceDescription();

@Nullable
BeanDefinition getOriginatingBeanDefinition();
```

&nbsp;&nbsp; `<bean>`元素标签拥有的**各种配置属性（class,scope,lazy-init）**，`BeanDefinition`接口中都提供了**相应的`beanClass`、`scope`、`lazyInit`属性**，`BeanDefinition`和`<bean>`中的属性是**一一对应**的。

## 3.1 BeanDefinition 的父关系

&nbsp;&nbsp; `BeanDefinition` 继承 `AttributeAccessor` 和 `BeanMetadataElement` 接口。两个接口定义如下：

* `org.springframework.cor.AttributeAccessor` 接口，定义了**与其它对象的（元数据）进行连接和访问的约定，即对属性的修改，包括获取、设置、删除**。

```java
// org.springframework.core.AttributeAccessor.java

public interface AttributeAccessor {

	void setAttribute(String name, @Nullable Object value);

	@Nullable
	Object getAttribute(String name);

	@Nullable
	Object removeAttribute(String name);

	boolean hasAttribute(String name);

	String[] attributeNames();

}
```

*  `org.springframework.beans.BeanMetadataElement` 接口，`Bean` **元对象持有的配置元素**可以通过 `#getSource()` 方法来获取。 

```java
// org.springframework.beans.BeanMetadataElement.java

public interface BeanMetadataElement {

	@Nullable
	Object getSource();

}
```

## 3.2 BeanDefinition 的子关系

.<center>![Spring Beandefinition](source/Spring Beandefinition  结构.png)</center>

我们常用的三个实现类有：

- `org.springframework.beans.factory.support.ChildBeanDefinition`
- `org.springframework.beans.factory.support.RootBeanDefinition`
- `org.springframework.beans.factory.support.GenericBeanDefinition`
- `ChildBeanDefinition`、`RootBeanDefinition`、`GenericBeanDefinition` 三者都继承 `AbstractBeanDefinition` 抽象类，即 `AbstractBeanDefinition` 对三个子类的共同的类信息进行抽象。
- 如果配置文件中定义了父 `<bean>` 和 子 `<bean>` ，则父 `<bean>` 用 `RootBeanDefinition` 表示，子 `<bean>` 用 `ChildBeanDefinition `表示，而没有父 `<bean>` 的就使用`RootBeanDefinition` 表示。
- `GenericBeanDefinition` 为一站式服务类。

# 4. 解析 Bean 标签

&nbsp;&nbsp; 继续解析我们的`<bean>`标签，创建完 `GenericBeanDefinition` 实例后，再调用 `#parseBeanDefinitionAttributes(Element ele, String beanName, BeanDefinition containingBean, AbstractBeanDefinition bd)` 方法，该方法将创建好的 `GenericBeanDefinition` 实例当做参数，对 `bean` 标签的所有属性进行解析。

```java
// org.springframework.beans.factory.xml.BeanDefinitionParserDelegate.java

public AbstractBeanDefinition parseBeanDefinitionAttributes(Element ele, String beanName,
		@Nullable BeanDefinition containingBean, AbstractBeanDefinition bd) {

	// 解析 scope 属性
	if (ele.hasAttribute(SINGLETON_ATTRIBUTE)) {
		error("Old 1.x 'singleton' attribute in use - upgrade to 'scope' declaration", ele);
	}else if (ele.hasAttribute(SCOPE_ATTRIBUTE)) {
		bd.setScope(ele.getAttribute(SCOPE_ATTRIBUTE));
	}else if (containingBean != null) {
		// 和父类 scope 一样
		bd.setScope(containingBean.getScope());
	}

	// 解析 abstract 属性
	if (ele.hasAttribute(ABSTRACT_ATTRIBUTE)) {
		bd.setAbstract(TRUE_VALUE.equals(ele.getAttribute(ABSTRACT_ATTRIBUTE)));
	}

	// 解析 lazy-init 属性
	String lazyInit = ele.getAttribute(LAZY_INIT_ATTRIBUTE);
	if (isDefaultValue(lazyInit)) {
		lazyInit = this.defaults.getLazyInit();
	}
	bd.setLazyInit(TRUE_VALUE.equals(lazyInit));

	// 解析 autowire 属性
	String autowire = ele.getAttribute(AUTOWIRE_ATTRIBUTE);
	bd.setAutowireMode(getAutowireMode(autowire));

	// 解析 depends-on 属性
	if (ele.hasAttribute(DEPENDS_ON_ATTRIBUTE)) {
		String dependsOn = ele.getAttribute(DEPENDS_ON_ATTRIBUTE);
		bd.setDependsOn(StringUtils.tokenizeToStringArray(dependsOn, MULTI_VALUE_ATTRIBUTE_DELIMITERS));
	}

	// 解析 autowire-candidate 属性
	String autowireCandidate = ele.getAttribute(AUTOWIRE_CANDIDATE_ATTRIBUTE);
	if (isDefaultValue(autowireCandidate)) {
		String candidatePattern = this.defaults.getAutowireCandidates();
		if (candidatePattern != null) {
			String[] patterns = StringUtils.commaDelimitedListToStringArray(candidatePattern);
			bd.setAutowireCandidate(PatternMatchUtils.simpleMatch(patterns, beanName));
		}
	}else {
		bd.setAutowireCandidate(TRUE_VALUE.equals(autowireCandidate));
	}

	// 解析 primary 标签
	if (ele.hasAttribute(PRIMARY_ATTRIBUTE)) {
		bd.setPrimary(TRUE_VALUE.equals(ele.getAttribute(PRIMARY_ATTRIBUTE)));
	}

	// 解析 init-method 属性
	if (ele.hasAttribute(INIT_METHOD_ATTRIBUTE)) {
		String initMethodName = ele.getAttribute(INIT_METHOD_ATTRIBUTE);
		bd.setInitMethodName(initMethodName);
	}
	else if (this.defaults.getInitMethod() != null) {
		bd.setInitMethodName(this.defaults.getInitMethod());
		bd.setEnforceInitMethod(false);
	}

	// 解析 destroy-method 属性
	if (ele.hasAttribute(DESTROY_METHOD_ATTRIBUTE)) {
		String destroyMethodName = ele.getAttribute(DESTROY_METHOD_ATTRIBUTE);
		bd.setDestroyMethodName(destroyMethodName);
	}
	else if (this.defaults.getDestroyMethod() != null) {
		bd.setDestroyMethodName(this.defaults.getDestroyMethod());
		bd.setEnforceDestroyMethod(false);
	}

	// 解析 factory-method 属性
	if (ele.hasAttribute(FACTORY_METHOD_ATTRIBUTE)) {
		bd.setFactoryMethodName(ele.getAttribute(FACTORY_METHOD_ATTRIBUTE));
	}
	if (ele.hasAttribute(FACTORY_BEAN_ATTRIBUTE)) {
		bd.setFactoryBeanName(ele.getAttribute(FACTORY_BEAN_ATTRIBUTE));
	}

	return bd;
}
```



