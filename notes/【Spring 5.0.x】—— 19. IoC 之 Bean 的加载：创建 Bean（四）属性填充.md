> 参考网址：<http://cmsblogs.com/?p=2885>

#### 目录

* [1. populateBean](#1)
  * [1.1 自动注入](#1.1)
    * [1.1.1 autowireByName](#1.1.1)
    * [1.1.2 autowireByType](#1.1.2)
      * [1.1.2.1 resolveDependency](#1.1.2.1)
  * [1.2 applyPropertyValues](#1.2)

****

&nbsp;&nbsp;  `#doCreateBean(...)` 方法，主要用于完成 `bean` 的**创建**和**初始化**工作

&nbsp;&nbsp; 我们可以将其分为四个过程 

*  `#createBeanInstance(String beanName, RootBeanDefinition mbd, Object[] args)` 方法，**实例化 `bean`**
*  **循环依赖的处理** 
*  `#populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw)` 方法，进行**属性填充** 
*  `#initializeBean(final String beanName, final Object bean, RootBeanDefinition mbd)` 方法，**初始化 `Bean`**

&nbsp;&nbsp; 这里分析**属性填充**，也就是 `#populateBean(...)` 方法。该函数的作用是将 `BeanDefinition` 中的属性值赋值给 `BeanWrapper` 实例对象 

<span id = "1"></span>
# 1. populateBean

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
	// 没有实例化对象
	if (bw == null) {
		// 有属性，则抛出 BeanCreationException 异常
		if (mbd.hasPropertyValues()) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
		}else {
			// 没有属性，直接 return 返回
			return;
		}
	}

	// <1> 在设置属性之前给 InstantiationAwareBeanPostProcessors 最后一次改变 bean 的机会
	if (!mbd.isSynthetic()// bean 不是"合成"的，即未由应用程序本身定义
			&& hasInstantiationAwareBeanPostProcessors()) {// 是否持有 InstantiationAwareBeanPostProcessor
		// 迭代所有的 BeanPostProcessors
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			// 如果为 InstantiationAwareBeanPostProcessor
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				// 返回值为是否继续填充 bean
				// postProcessAfterInstantiation：如果应该在 bean上面设置属性则返回 true，否则返回 false
				// 一般情况下，应该是返回true 。
				// 返回 false 的话，将会阻止在此 Bean 实例上调用任何后续的 InstantiationAwareBeanPostProcessor 实例
				if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					return;
				}
			}
		}
	}

	// bean 的属性值
	PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

	int resolvedAutowireMode = mbd.getResolvedAutowireMode();
	// <2> 自动注入
	if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
		// 将 PropertyValues 封装成 MutablePropertyValues 对象
		// MutablePropertyValues 允许对属性进行简单的操作，并提供构造函数以支持Map的深度复制和构造。
		MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

		// 根据名称自动注入
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
			autowireByName(beanName, mbd, bw, newPvs);
		}

		// 根据类型自动注入
		if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			autowireByType(beanName, mbd, bw, newPvs);
		}
		pvs = newPvs;
	}

	// 是否已经注册了 InstantiationAwareBeanPostProcessors
	boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
	// 是否需要进行【依赖检查】
	boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

	// <3> BeanPostProcessor 处理
	if (hasInstAwareBpps || needsDepCheck) {
		if (pvs == null) {
			pvs = mbd.getPropertyValues();
		}
		PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
		if (hasInstAwareBpps) {
			// 遍历 BeanPostProcessor 数组
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
					if (pvs == null) {
						return;
					}
				}
			}
		}
		// <4> 依赖检查
		if (needsDepCheck) {
			// 依赖检查，对应 depends-on 属性
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}
	}

	// <5> 将属性应用到 bean 中
	if (pvs != null) {
		applyPropertyValues(beanName, mbd, bw, pvs);
	}
}
```

&nbsp;&nbsp;  处理流程如下 

*  `<1>` ，根据 `hasInstantiationAwareBeanPostProcessors` 属性来判断，是否需要在注入属性之前给 `InstantiationAwareBeanPostProcessors` 最后一次改变 `bean` 的机会。**此过程可以控制 Spring 是否继续进行属性填充** 
*  统一存入到 `PropertyValues` 中，`PropertyValues` 用于描述 `bean` 的属性 
*  `<2>` ，根据注入类型( `AbstractBeanDefinition#getResolvedAutowireMode()` 方法的返回值 )的不同来判断 
  *  根据**名称**来自动注入（`#autowireByName(...)`）。见 [「1.1 自动注入」](#1.1)  
  *  根据**类型**来自动注入（`#autowireByType(...)`） 。见 [「1.1 自动注入」](#1.1)
*  `<3>` ，进行 `BeanPostProcessor` 处理 
*  `<4>` ，依赖检测 
*  `<5>` ，将所有 `PropertyValues` 中的属性，填充到 `BeanWrapper` 中 

<span id = "1.1"></span>
## 1.1 自动注入

 &nbsp;&nbsp; `Spring` 会根据注入类型（ `byName` / `byType` ）的不同，调用不同的方法来注入**属性值**

```java 
// org.springframework.beans.factory.support.AbstractBeanDefinition.java

/**
 * 注入模式(自动注入),默认不进行自动装配
 */
private int autowireMode = AUTOWIRE_NO;

public int getResolvedAutowireMode() {
	// 自动检测模式，获得对应的检测模式
	if (this.autowireMode == AUTOWIRE_AUTODETECT) {
		// Work out whether to apply setter autowiring or constructor autowiring.
		// If it has a no-arg constructor it's deemed to be setter autowiring,
		// otherwise we'll try constructor autowiring.
		Constructor<?>[] constructors = getBeanClass().getConstructors();
		for (Constructor<?> constructor : constructors) {
			if (constructor.getParameterCount() == 0) {
				return AUTOWIRE_BY_TYPE;
			}
		}
		return AUTOWIRE_CONSTRUCTOR;
	}
	else {
		return this.autowireMode;
	}
}
```

<span id = "1.1.1"></span>
### 1.1.1 autowireByName

&nbsp;&nbsp;  `#autowireByName(String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs)` 方法，根据**属性名称**，完成自动依赖注入

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected void autowireByName(
		String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

	// <1> 对 Bean 对象中非简单属性
	String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
	// 遍历 propertyName 数组
	for (String propertyName : propertyNames) {
		// 如果容器中包含指定名称的 bean，则将该 bean 注入到 bean中
		if (containsBean(propertyName)) {
			// 递归初始化相关 bean
			Object bean = getBean(propertyName);
			// 为指定名称的属性赋予属性值
			pvs.add(propertyName, bean);
			// 属性依赖注入
			registerDependentBean(propertyName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Added autowiring by name from bean name '" + beanName +
						"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
			}
		}else {
			if (logger.isTraceEnabled()) {
				logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
						"' by name: no matching bean found");
			}
		}
	}
}
```

&nbsp;&nbsp;  `<1>` 处，该方法逻辑很简单，获取该 `bean` 的**非简单属性**。**什么叫做非简单属性呢**？就是类型为**对象类型**的属性，但是这里并不是将所有的对象类型都都会找到，比如 8 个原始类型，`String` 类型 ，`Number`类型、`Date`类型、`URL`类型、`URI`类型等都会被忽略 

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
	// 创建 result 集合
	Set<String> result = new TreeSet<>();
	PropertyValues pvs = mbd.getPropertyValues();
	// 遍历 PropertyDescriptor 数组
	PropertyDescriptor[] pds = bw.getPropertyDescriptors();
	for (PropertyDescriptor pd : pds) {
		if (pd.getWriteMethod() != null  // 有可写方法
				&& !isExcludedFromDependencyCheck(pd) // 依赖检测中没有被忽略
				&& !pvs.contains(pd.getName())  // pvs 不包含该属性名
				&& !BeanUtils.isSimpleProperty(pd.getPropertyType())) { // 不是简单属性类型
			// 添加到 result 中
			result.add(pd.getName());
		}
	}
	return StringUtils.toStringArray(result);
}
```

*  过滤**条件**为：有可写方法、依赖检测中没有被忽略、不是简单属性类型 

*  过滤**结果**为：其实这里获取的就是需要依赖注入的属性 

&nbsp;&nbsp;  获取需要依赖注入的属性后，通过**迭代**、**递归**的方式初始化相关的 `bean` ，然后调用 `#registerDependentBean(String beanName, String dependentBeanName)` 方法，完成注册依赖 

```java
// org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.java

/**
 * Map between dependent bean names: bean name to Set of dependent bean names.
 *
 * 保存的是依赖 beanName 之间的映射关系：beanName - > 依赖 beanName 的集合
 */
private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

/**
 * Map between depending bean names: bean name to Set of bean names for the bean's dependencies.
 *
 * 保存的是依赖 beanName 之间的映射关系：依赖 beanName - > beanName 的集合
 */
private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);

public void registerDependentBean(String beanName, String dependentBeanName) {
	// 获取 beanName
	String canonicalName = canonicalName(beanName);

	// 添加 <canonicalName, <dependentBeanName>> 到 dependentBeanMap 中
	synchronized (this.dependentBeanMap) {
		Set<String> dependentBeans =
				this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
		if (!dependentBeans.add(dependentBeanName)) {
			return;
		}
	}

	// 添加 <dependentBeanName, <canonicalName>> 到 dependenciesForBeanMap 中
	synchronized (this.dependenciesForBeanMap) {
		Set<String> dependenciesForBean =
				this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
		dependenciesForBean.add(canonicalName);
	}
}
```

<span id = "1.1.2"></span>
### 1.1.2 autowireByType

&nbsp;&nbsp;  `#autowireByType(String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs)` 方法，根据**属性类型**，完成自动依赖注入

```java 
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected void autowireByType(
		String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

	// 获取 TypeConverter 实例
	// 使用自定义的 TypeConverter，用于取代默认的 PropertyEditor 机制
	TypeConverter converter = getCustomTypeConverter();
	if (converter == null) {
		converter = bw;
	}

	Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
	// 获取非简单属性
	String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
	// 遍历 propertyName 数组
	for (String propertyName : propertyNames) {
		try {
			// 获取 PropertyDescriptor 实例
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			// Don't try autowiring by type for type Object: never makes sense,
			// even if it technically is a unsatisfied, non-simple property.
			// 不要尝试按类型
			if (Object.class != pd.getPropertyType()) {
				// 探测指定属性的 set 方法
				MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
				// Do not allow eager init for type matching in case of a prioritized post-processor.
				boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
				DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
				// 解析指定 beanName 的属性所匹配的值，并把解析到的属性名称存储在 autowiredBeanNames 中
				// 当属性存在过个封装 bean 时将会找到所有匹配的 bean 并将其注入
				Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
				if (autowiredArgument != null) {
					pvs.add(propertyName, autowiredArgument);
				}
				// 遍历 autowiredBeanName 数组
				for (String autowiredBeanName : autowiredBeanNames) {
					// 属性依赖注入
					registerDependentBean(autowiredBeanName, beanName);
					if (logger.isDebugEnabled()) {
						logger.debug("Autowiring by type from bean name '" + beanName + "' via property '" +
								propertyName + "' to bean named '" + autowiredBeanName + "'");
					}
				}
				// 清空 autowiredBeanName 数组
				autowiredBeanNames.clear();
			}
		}
		catch (BeansException ex) {
			throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
		}
	}
}
```

&nbsp;&nbsp;  主要过程和根据**属性名称**自动注入**差不多**，都是找到需要依赖注入的属性，然后通过**迭代**的方式寻找所匹配的 `bean`，最后调用 `#registerDependentBean(...)` 方法，来注册依赖。

<span id = "1.1.2.1"></span>
#### 1.1.2.1 resolveDependency

```java
// org.springframework.beans.factory.support.DefaultListableBeanFactory.java

@Nullable
private static Class<?> javaxInjectProviderClass;

static {
	try {
		javaxInjectProviderClass =
				ClassUtils.forName("javax.inject.Provider", DefaultListableBeanFactory.class.getClassLoader());
	}
	catch (ClassNotFoundException ex) {
		// JSR-330 API not available - Provider interface simply not supported then.
		javaxInjectProviderClass = null;
	}
}


@Override
@Nullable
public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
		@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

	// 初始化参数名称发现器，该方法并不会在这个时候尝试检索参数名称
	// getParameterNameDiscoverer 返回 parameterNameDiscoverer 实例，parameterNameDiscoverer 方法参数名称的解析器
	descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
	// 依赖类型为 Optional 类型
	if (Optional.class == descriptor.getDependencyType()) {
		return createOptionalDependency(descriptor, requestingBeanName);
	}else if (ObjectFactory.class == descriptor.getDependencyType() ||
			ObjectProvider.class == descriptor.getDependencyType()) {
		// 依赖类型为ObjectFactory、ObjectProvider
		return new DependencyObjectProvider(descriptor, requestingBeanName);
	}else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
		// javaxInjectProviderClass 类注入的特殊处理
		return new Jsr330ProviderFactory().createDependencyProvider(descriptor, requestingBeanName);
	}else {
		// 为实际依赖关系目标的延迟解析构建代理
		// 默认实现返回 null
		Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
				descriptor, requestingBeanName);
		if (result == null) {
			// 通用处理逻辑
			result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
		}
		return result;
	}
}
```

&nbsp;&nbsp;  **通用处理逻辑** `#doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName, Set autowiredBeanNames, TypeConverter typeConverter)` 方法 

```java
// org.springframework.beans.factory.support.DefaultListableBeanFactory.java

@Nullable
public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
		@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

	// 注入点
	InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
	try {
		// 针对给定的工厂给定一个快捷实现的方式，例如考虑一些预先解析的信息
		// 在进入所有bean的常规类型匹配算法之前，解析算法将首先尝试通过此方法解析快捷方式。
		// 子类可以覆盖此方法
		Object shortcut = descriptor.resolveShortcut(this);
		if (shortcut != null) {
			// 返回快捷的解析信息
			return shortcut;
		}

		// 依赖的类型
		Class<?> type = descriptor.getDependencyType();
		// 支持 Spring 的注解 @value
		Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
		if (value != null) {
			if (value instanceof String) {
				String strVal = resolveEmbeddedValue((String) value);
				BeanDefinition bd = (beanName != null && containsBean(beanName) ? getMergedBeanDefinition(beanName) : null);
				value = evaluateBeanDefinitionString(strVal, bd);
			}
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			return (descriptor.getField() != null ?
					converter.convertIfNecessary(value, type, descriptor.getField()) :
					converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
		}

		// 解析复合 bean，其实就是对 bean 的属性进行解析
		// 包括：数组、Collection 、Map 类型
		Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
		if (multipleBeans != null) {
			return multipleBeans;
		}

		// 查找与类型相匹配的 bean
		// 返回值构成为：key = 匹配的 beanName，value = beanName 对应的实例化 bean
		Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
		// 没有找到，检验 @autowire  的 require 是否为 true
		if (matchingBeans.isEmpty()) {
			// 如果 @autowire 的 require 属性为 true ，但是没有找到相应的匹配项，则抛出异常
			if (isRequired(descriptor)) {
				raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
			}
			return null;
		}

		String autowiredBeanName;
		Object instanceCandidate;

		if (matchingBeans.size() > 1) {
			// 确认给定 bean autowire 的候选者
			// 按照 @Primary 和 @Priority 的顺序
			autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
			if (autowiredBeanName == null) {
				if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
					// 唯一性处理
					return descriptor.resolveNotUnique(type, matchingBeans);
				}else {
					// In case of an optional Collection/Map, silently ignore a non-unique case:
					// possibly it was meant to be an empty collection of multiple regular beans
					// (before 4.3 in particular when we didn't even look for collection beans).
					// 在可选的Collection / Map的情况下，默默地忽略一个非唯一的情况：可能它是一个多个常规bean的空集合
					return null;
				}
			}
			instanceCandidate = matchingBeans.get(autowiredBeanName);
		}else {
			// We have exactly one match.
			Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
			autowiredBeanName = entry.getKey();
			instanceCandidate = entry.getValue();
		}

		if (autowiredBeanNames != null) {
			autowiredBeanNames.add(autowiredBeanName);
		}
		if (instanceCandidate instanceof Class) {
			instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
		}
		Object result = instanceCandidate;
		if (result instanceof NullBean) {
			if (isRequired(descriptor)) {
				raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
			}
			result = null;
		}
		if (!ClassUtils.isAssignableValue(type, result)) {
			throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
		}
		return result;
	}
	finally {
		ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
	}
}
```

&nbsp;&nbsp;  到这里就已经完成了所有属性的注入了。`populateBean()` 该方法就已经完成了一大半工作 

&nbsp;&nbsp;  下一步，则是对依赖 `bean` 的**依赖检测**和 `PostProcessor` 处理，**这个我们之后分析** 

&nbsp;&nbsp;  接下来，我们分析该方法的最后一步：`#applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs)` 方法 

<span id = "1.2"></span>
## 1.2 applyPropertyValues

 &nbsp;&nbsp; 上面只是完成了所有注入属性的获取，将获取的属性封装在 `PropertyValues` 的实例对象 `pvs` 中，并没有应用到已经实例化的 `bean` 中。而 `#applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs)` 方法，才完成这一步骤

 ```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
	if (pvs.isEmpty()) {
		return;
	}

	// 设置 BeanWrapperImpl 的 SecurityContext 属性
	if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
		((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
	}

	// MutablePropertyValues 类型属性
	MutablePropertyValues mpvs = null;
	// 获得 original
	List<PropertyValue> original;

	// 获得 original
	if (pvs instanceof MutablePropertyValues) {
		mpvs = (MutablePropertyValues) pvs;
		// 属性值已经转换
		if (mpvs.isConverted()) {
			// Shortcut: use the pre-converted values as-is.
			try {
				// 为实例化对象设置属性值 ，依赖注入真真正正地实现在此！！！！！
				bw.setPropertyValues(mpvs);
				return;
			}
			catch (BeansException ex) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Error setting property values", ex);
			}
		}
		original = mpvs.getPropertyValueList();
	}else {
		// 如果 pvs 不是 MutablePropertyValues 类型，则直接使用原始类型
		original = Arrays.asList(pvs.getPropertyValues());
	}

	// 获取 TypeConverter = 获取用户自定义的类型转换
	TypeConverter converter = getCustomTypeConverter();
	if (converter == null) {
		converter = bw;
	}
	// 获取对应的解析器
	BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

	// Create a deep copy, resolving any references for values.
	List<PropertyValue> deepCopy = new ArrayList<>(original.size());
	boolean resolveNecessary = false;
	// 遍历属性，将属性转换为对应类的对应属性的类型
	for (PropertyValue pv : original) {
		if (pv.isConverted()) {
			// 属性值不需要转换
			deepCopy.add(pv);
		}else {
			// 属性值需要转换

			String propertyName = pv.getName();
			// 原始的属性值，即转换之前的属性值
			Object originalValue = pv.getValue();
			// 转换属性值，例如将引用转换为IoC容器中实例化对象引用 ！！！！！ 对属性值的解析！！
			Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
			// 转换之后的属性值
			Object convertedValue = resolvedValue;
			boolean convertible = bw.isWritableProperty(propertyName) &&
					!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);// 属性值是否可以转换
			if (convertible) {
				// 使用用户自定义的类型转换器转换属性值
				convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
			}
			// Possibly store converted value in merged bean definition,
			// in order to avoid re-conversion for every created bean instance.
			// 存储转换后的属性值，避免每次属性注入时的转换工作
			if (resolvedValue == originalValue) {
				if (convertible) {
					// 设置属性转换之后的值
					pv.setConvertedValue(convertedValue);
				}
				deepCopy.add(pv);
			}else if (convertible && originalValue instanceof TypedStringValue &&
					!((TypedStringValue) originalValue).isDynamic() &&
					!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
				// 属性是可转换的，且属性原始值是字符串类型，且属性的原始类型值不是
				// 动态生成的字符串，且属性的原始值不是集合或者数组类型
				pv.setConvertedValue(convertedValue);
				deepCopy.add(pv);
			}else {
				resolveNecessary = true;
				// 重新封装属性的值
				deepCopy.add(new PropertyValue(pv, convertedValue));
			}
		}
	}
	// 标记属性值已经转换过
	if (mpvs != null && !resolveNecessary) {
		mpvs.setConverted();
	}

	// 进行属性依赖注入，依赖注入的真真正正实现依赖的注入方法在此！！！
	try {
		bw.setPropertyValues(new MutablePropertyValues(deepCopy));
	}
	catch (BeansException ex) {
		throw new BeanCreationException(
				mbd.getResourceDescription(), beanName, "Error setting property values", ex);
	}
}
 ```

&nbsp;&nbsp;  `#applyPropertyValues(...)` 方法（完成属性转换） 总体逻辑如下

*  属性值类型**不需要**转换时，不需要解析属性值，直接准备进行依赖注入 
*  属性值**需要**进行类型转换时，如对其他对象的引用等，首先需要解析属性值，然后对解析后的属性值进行依赖注入 

>  `#resolveValueIfNecessary(...)`方法是对属性值的解析 