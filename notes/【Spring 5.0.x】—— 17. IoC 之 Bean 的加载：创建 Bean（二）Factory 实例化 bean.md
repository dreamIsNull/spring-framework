> 参考网址：<http://cmsblogs.com/?p=2848>

#### 目录

* [1. createBeanInstance](#1)
  * [1.1 obtainFromSupplier](#1.1)
    * [1.1.1 Supplier](#1.1.1)
    * [1.1.2 obtainFromSupplier](#1.1.2)
  * [1.2 instantiateUsingFactoryMethod](#1.2)
    * [1.2.1 ConstructorResolver](#1.2.1)
      * [1.2.1.1 确定工厂对象](#1.2.1.1)
      * [1.2.1.2 构造参数确认](#1.2.1.2)
        * [1.2.1.2.1 explicitArgs 参数](#1.2.1.2.1)
        * [1.2.1.2.2 缓存中获取](#1.2.1.2.2)
        * [1.2.1.2.3 配置文件中解析](#1.2.1.2.3)
      * [1.2.1.3 构造函数确认](#1.2.1.3)
      * [1.2.1.4 创建 bean 实例](#1.2.1.4)

****

<span id = "1"></span>
# 1. createBeanInstance

 &nbsp;&nbsp; 创建 `bean` 过程中的**第一个**步骤：**实例化 `bean`**，对应的方法为 `#createBeanInstance(String beanName, RootBeanDefinition mbd, Object[] args)` 

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
	// 解析 bean ，将 bean 类名解析为 class 引用。
	Class<?> beanClass = resolveBeanClass(mbd, beanName);

	if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
		throw new BeanCreationException(mbd.getResourceDescription(), beanName,
				"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
	}

	// <1> 如果存在 Supplier 回调，则使用给定的回调方法初始化策略
	Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
	if (instanceSupplier != null) {
		return obtainFromSupplier(instanceSupplier, beanName);
	}

	// <2> 使用 FactoryBean 的 factory-method 来创建，支持静态工厂和实例工厂
	if (mbd.getFactoryMethodName() != null) {
		return instantiateUsingFactoryMethod(beanName, mbd, args);
	}

	// <3> Shortcut when re-creating the same bean...
	boolean resolved = false;
	boolean autowireNecessary = false;
	if (args == null) {
		// constructorArgumentLock 构造函数的常用锁
		synchronized (mbd.constructorArgumentLock) {
			// 如果已缓存的解析的构造函数或者工厂方法不为空，则可以利用构造函数解析
			// 因为需要根据参数确认到底使用哪个构造函数，该过程比较消耗性能，所有采用缓存机制
			if (mbd.resolvedConstructorOrFactoryMethod != null) {
				resolved = true;
				autowireNecessary = mbd.constructorArgumentsResolved;
			}
		}
	}
	// 已经解析好了，直接注入即可
	if (resolved) {
		if (autowireNecessary) {
			// <3.1> autowire 自动注入，调用构造函数自动注入
			return autowireConstructor(beanName, mbd, null, null);
		}else {
			// <3.2> 使用默认构造函数构造
			return instantiateBean(beanName, mbd);
		}
	}

	// <4> 确定解析的构造函数
	// 主要是检查已经注册的 SmartInstantiationAwareBeanPostProcessor
	Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
	// <4.1> 有参数情况时，创建 Bean 。先利用参数个数，类型等，确定最精确匹配的构造方法。
	if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
			mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
		return autowireConstructor(beanName, mbd, ctors, args);
	}

	// <4.2> 有参数时，又没获取到构造方法，则只能调用无参构造方法来创建实例了(兜底方法)
	return instantiateBean(beanName, mbd);
}
```

&nbsp;&nbsp;  实例化 `Bean` 对象，是一个**复杂**的过程，其主要的逻辑为 

*  `<1>` 处，如果存在 `Supplier` 回调，则调用 `#obtainFromSupplier(Supplier instanceSupplier, String beanName)` 方法，进行初始化 。 见 [「1.1 obtainFromSupplier」](#1.1) 

*  `<2>` 处，如果存在工厂方法，则调用`#instantiateUsingFactoryMethod(String beanName,RootBeanDefinition mbd,Object[] explicitArgs)`使用工厂方法进行初始化。  见 [「1.2 instantiateUsingFactoryMethod」](#1.2) 

*  `<3>` 处，首先判断缓存，如果**缓存中存在**，即已经解析过了，则直接使用已经解析了的。根据 `constructorArgumentsResolved` 参数来判断 

  *  `<3.1>` 处，是使用构造函数自动注入，即调用 `#autowireConstructor(String beanName, RootBeanDefinition mbd, Constructor[] ctors, Object[] explicitArgs)` 方法 。见 [《【Spring 5.0.x】—— 18. IoC 之 Bean 的加载：创建 Bean（三）构造函数实例化 bean》]()  
  *  `<3.2>` 处，还是默认构造函数，即调用 `#instantiateBean(final String beanName, final RootBeanDefinition mbd)` 方法 。见 [《【Spring 5.0.x】—— 18. IoC 之 Bean 的加载：创建 Bean（三）构造函数实例化 bean》]() 

*  `<4>` 处，如果**缓存中没有**，则需要先确定到底使用哪个构造函数来完成解析工作，因为一个类有多个构造函数，每个构造函数都有不同的构造参数，所以需要根据参数来锁定构造函数并完成初始化 

  *  `<4.1>` 处，如果存在参数，则使用相应的带有参数的构造函数，即调用 `#autowireConstructor(String beanName, RootBeanDefinition mbd, Constructor[] ctors, Object[] explicitArgs)` 方法。 见 [《【Spring 5.0.x】—— 18. IoC 之 Bean 的加载：创建 Bean（三）构造函数实例化 bean》]() 
  *  `<4.2>` 处，否则，使用默认构造函数，即调用 `#instantiateBean(final String beanName, final RootBeanDefinition mbd)` 方法 。 见 [《【Spring 5.0.x】—— 18. IoC 之 Bean 的加载：创建 Bean（三）构造函数实例化 bean》]() 

<span id = "1.1"></span>
## 1.1 obtainFromSupplier

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

// <1> 如果存在 Supplier 回调，则使用给定的回调方法初始化策略
Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
if (instanceSupplier != null) {
	return obtainFromSupplier(instanceSupplier, beanName);
}
```

  &nbsp;&nbsp;  从 `BeanDefinition` 中获取 `Supplier` 对象。如果**不为空**，则调用 `#obtainFromSupplier(final String beanName, final RootBeanDefinition mbd)` 方法 

<span id = "1.1.1"></span>
### 1.1.1 Supplier

&nbsp;&nbsp;  `Supplier` 是什么呢？定义为`java.util.function.Supplier` 接口 

```java
// java.util.function.Supplier.java

@FunctionalInterface
public interface Supplier<T> {

    T get();
}
```

*  `Supplier` 接口仅有一个功能性的 `#get()` 方法，该方法会返回一个 ` <T> ` 类型的对象，有点儿**类似工厂方法** 
*  这个接口有什么作用？用于**指定创建 `bean` 的回调**。如果我们设置了这样的回调，那么其他的构造器或者工厂方法都会没有用 

&nbsp;&nbsp;  在什么地方设置该 `Supplier` 参数呢？`Spring` 提供了相应的 `setter` 方法 

```java
// org.springframework.beans.factory.support.AbstractBeanDefinition.java

/**
 * 创建 Bean 的 Supplier 对象
 */
@Nullable
private Supplier<?> instanceSupplier;

public void setInstanceSupplier(@Nullable Supplier<?> instanceSupplier) {
	this.instanceSupplier = instanceSupplier;
}
```

&nbsp;&nbsp;  在构造 `BeanDefinition` 对象的时候，设置了 `instanceSupplier` 该值，代码如下（以 `RootBeanDefinition` 为例）

```java 
// org.springframework.beans.factory.support.RootBeanDefinition.java

public <T> RootBeanDefinition(@Nullable Class<T> beanClass, String scope, @Nullable Supplier<T> instanceSupplier) {
	super();
	setBeanClass(beanClass);
	setScope(scope);
	// 设置 instanceSupplier 属性
	setInstanceSupplier(instanceSupplier);
}
```

<span id = "1.1.2"></span>
### 1.1.2 obtainFromSupplier

 &nbsp;&nbsp; 如果设置了 `instanceSupplier` 属性，则可以调用 `#obtainFromSupplier(Supplier instanceSupplier, String beanName)` 方法，完成 `Bean` 的初始化 

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

/**
 * 当前线程，正在创建的 Bean 对象的名字
 *
 * The name of the currently created bean, for implicit dependency registration
 * on getBean etc invocations triggered from a user-specified Supplier callback.
 */
private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
	// 获得原创建的 Bean 的对象名
	String outerBean = this.currentlyCreatedBean.get();
	// 设置新的 Bean 的对象名，到 currentlyCreatedBean 中
	this.currentlyCreatedBean.set(beanName);
	Object instance;
	try {
		// <1> 调用 Supplier 的 get()，返回一个 Bean 对象
		instance = instanceSupplier.get();
	}
	finally {
		// 设置原创建的 Bean 的对象名，到 currentlyCreatedBean 中
		if (outerBean != null) {
			this.currentlyCreatedBean.set(outerBean);
		}else {
			this.currentlyCreatedBean.remove();
		}
	}
	// <3> 初始化 BeanWrapper 对象
	BeanWrapper bw = new BeanWrapperImpl(instance);
	// <3> 初始化 BeanWrapper 对象
	initBeanWrapper(bw);
	return bw;
}
```

&nbsp;&nbsp; 流程如下

*  `<1>` 首先，调用 `Supplier` 的 `get()` 方法，获得一个 `Bean` 实例对象 
*  `<2>` 然后，根据该实例对象构造一个 `BeanWrapper` 对象 `bw` 
*  `<3>` 最后，**初始化该对象** 

<span id = "1.2"></span>
## 1.2 instantiateUsingFactoryMethod

 &nbsp;&nbsp; 如果存在工厂方法，则调用 `#instantiateUsingFactoryMethod(String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs)` 方法完成 `bean` 的初始化工作 

```java
// org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.java

protected BeanWrapper instantiateUsingFactoryMethod(
		String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

	return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
}
```

&nbsp;&nbsp;  构造一个 `ConstructorResolver` 对象，然后调用其 `#instantiateUsingFactoryMethod(EvaluationContext context, String typeName, List argumentTypes)` 方法 

<span id = "1.2.1"></span>
### 1.2.1 ConstructorResolver

&nbsp;&nbsp;  `org.springframework.beans.factory.support.ConstructorResolver` 是**构造方法或者工厂类初始化 `bean` 的委托类** 

```java
// org.springframework.beans.factory.support.ConstructorResolver.java

public BeanWrapper instantiateUsingFactoryMethod(
		String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

	// 构造 BeanWrapperImpl 对象
	BeanWrapperImpl bw = new BeanWrapperImpl();
	// 初始化 BeanWrapperImpl
	// 向BeanWrapper对象中添加 ConversionService 对象和属性编辑器 PropertyEditor 对象
	this.beanFactory.initBeanWrapper(bw);

	// <1> 获得 factoryBean、factoryClass、isStatic、factoryBeanName 属性
	Object factoryBean;
	Class<?> factoryClass;
	boolean isStatic;

	String factoryBeanName = mbd.getFactoryBeanName();
	// 工厂名不为空
	if (factoryBeanName != null) {
		if (factoryBeanName.equals(beanName)) {
			// 抛出 BeanDefinitionStoreException 异常
			// factoryBean创建出来的是工厂自身，会报异常，这样貌似等于无限递归创建了
			throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
					"factory-bean reference points back to the same bean definition");
		}
		// 获取工厂实例
		factoryBean = this.beanFactory.getBean(factoryBeanName);
		if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
			// 抛出 ImplicitlyAppearedSingletonException 异常
			throw new ImplicitlyAppearedSingletonException();
		}
		factoryClass = factoryBean.getClass();
		isStatic = false;
	}else {
		// 工厂名为空，则其可能是一个静态工厂
		// 静态工厂创建bean，必须要提供工厂的全类名
		if (!mbd.hasBeanClass()) {
			throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
					"bean definition declares neither a bean class nor a factory-bean reference");
		}
		factoryBean = null;
		factoryClass = mbd.getBeanClass();
		isStatic = true;
	}

	// <2> 获得 factoryMethodToUse、argsHolderToUse、argsToUse 属性
	Method factoryMethodToUse = null; // 工厂方法
	ArgumentsHolder argsHolderToUse = null;
	Object[] argsToUse = null; // 参数

	// <2.1> 如果指定了构造参数则直接使用
	// 在调用 getBean 方法的时候指定了方法参数
	if (explicitArgs != null) {
		argsToUse = explicitArgs;
	}else {
		// 没有指定，则尝试从配置文件中解析
		Object[] argsToResolve = null;
		// <2.2> 首先尝试从缓存中获取
		synchronized (mbd.constructorArgumentLock) {
			// 获取缓存中的构造函数或者工厂方法
			factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;
			if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
				// 获取缓存中的构造参数
				argsToUse = mbd.resolvedConstructorArguments;
				if (argsToUse == null) {
					// 获取缓存中的构造函数参数的包可见字段
					argsToResolve = mbd.preparedConstructorArguments;
				}
			}
		}
		// 缓存中存在,则解析存储在 BeanDefinition 中的参数
		// 如给定方法的构造函数 A(int ,int )，则通过此方法后就会把配置文件中的("1","1")转换为 (1,1)
		// 缓存中的值可能是原始值也有可能是最终值
		if (argsToResolve != null) {
			argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve);
		}
	}

	// <3>
	if (factoryMethodToUse == null || argsToUse == null) {
		// 获取工厂方法的类全名称
		factoryClass = ClassUtils.getUserClass(factoryClass);

		// 获取所有待定方法
		Method[] rawCandidates = getCandidateMethods(factoryClass, mbd);
		// 检索所有方法，这里是对方法进行过滤
		List<Method> candidateList = new ArrayList<>();
		for (Method candidate : rawCandidates) {
			// 如果有static 且为工厂方法，则添加到 candidateSet 中
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
				candidateList.add(candidate);
			}
		}
		Method[] candidates = candidateList.toArray(new Method[0]);
		// 排序构造函数
		// public 构造函数优先参数数量降序，非 public 构造函数参数数量降序
		AutowireUtils.sortFactoryMethods(candidates);

		// 用于承载解析后的构造函数参数的值
		ConstructorArgumentValues resolvedValues = null;
		boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
		int minTypeDiffWeight = Integer.MAX_VALUE;
		Set<Method> ambiguousFactoryMethods = null;

		int minNrOfArgs;
		if (explicitArgs != null) {
			minNrOfArgs = explicitArgs.length;
		}else {
			// <2.3> getBean() 没有传递参数，则需要解析保存在 BeanDefinition 构造函数中指定的参数
			if (mbd.hasConstructorArgumentValues()) {
				// <2.3.1> 构造函数的参数
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
				resolvedValues = new ConstructorArgumentValues();
				// <2.3.2> 解析构造函数的参数
				// 将该 bean 的构造函数参数解析为 resolvedValues 对象，其中会涉及到其他 bean
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}
			else {
				minNrOfArgs = 0;
			}
		}

		// 记录 UnsatisfiedDependencyException 异常的集合
		LinkedList<UnsatisfiedDependencyException> causes = null;

		// 遍历 candidates 数组
		for (Method candidate : candidates) {
			// 方法体的参数
			Class<?>[] paramTypes = candidate.getParameterTypes();

			if (paramTypes.length >= minNrOfArgs) {
				// 保存参数的对象
				ArgumentsHolder argsHolder;

				// #getBean(...) 传递了参数
				if (explicitArgs != null) {
					// 显示给定参数，参数长度必须完全匹配
					if (paramTypes.length != explicitArgs.length) {
						continue;
					}
					// 根据参数创建参数持有者 ArgumentsHolder 对象
					argsHolder = new ArgumentsHolder(explicitArgs);
				}else {
					// 为提供参数，解析构造参数
					try {
						String[] paramNames = null;
						// 获取 ParameterNameDiscoverer 对象
						// ParameterNameDiscoverer 是用于解析方法和构造函数的参数名称的接口，为参数名称探测器
						ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
						// 获取指定构造函数的参数名称
						if (pnd != null) {
							paramNames = pnd.getParameterNames(candidate);
						}
						// 在已经解析的构造函数参数值的情况下，创建一个参数持有者 ArgumentsHolder 对象
						argsHolder = createArgumentArray(
								beanName, mbd, resolvedValues, bw, paramTypes, paramNames, candidate, autowiring);
					}catch (UnsatisfiedDependencyException ex) {
						// 若发生 UnsatisfiedDependencyException 异常，添加到 causes 中。
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
						}
						// Swallow and try next overloaded factory method.
						if (causes == null) {
							causes = new LinkedList<>();
						}
						causes.add(ex);
						// continue ，继续执行
						continue;
					}
				}

				// isLenientConstructorResolution 判断解析构造函数的时候是否以宽松模式还是严格模式
				// 严格模式：解析构造函数时，必须所有的都需要匹配，否则抛出异常
				// 宽松模式：使用具有"最接近的模式"进行匹配
				// typeDiffWeight：类型差异权重
				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				// 代表最接近的类型匹配，则选择作为构造函数
				if (typeDiffWeight < minTypeDiffWeight) {
					factoryMethodToUse = candidate;
					argsHolderToUse = argsHolder;
					argsToUse = argsHolder.arguments;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousFactoryMethods = null;
				}
				// 如果具有相同参数数量的方法具有相同的类型差异权重，则收集此类型选项
				// 但是，仅在非宽松构造函数解析模式下执行该检查，并显式忽略重写方法（具有相同的参数签名）
				else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
						!mbd.isLenientConstructorResolution() &&
						paramTypes.length == factoryMethodToUse.getParameterCount() &&
						!Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
					// 查找到多个可匹配的方法
					if (ambiguousFactoryMethods == null) {
						ambiguousFactoryMethods = new LinkedHashSet<>();
						ambiguousFactoryMethods.add(factoryMethodToUse);
					}
					ambiguousFactoryMethods.add(candidate);
				}
			}
		}

		// 没有可执行的工厂方法，抛出异常
		if (factoryMethodToUse == null) {
			if (causes != null) {
				UnsatisfiedDependencyException ex = causes.removeLast();
				for (Exception cause : causes) {
					this.beanFactory.onSuppressedException(cause);
				}
				throw ex;
			}
			List<String> argTypes = new ArrayList<>(minNrOfArgs);
			if (explicitArgs != null) {
				for (Object arg : explicitArgs) {
					argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
				}
			}else if (resolvedValues != null) {
				Set<ValueHolder> valueHolders = new LinkedHashSet<>(resolvedValues.getArgumentCount());
				valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());
				valueHolders.addAll(resolvedValues.getGenericArgumentValues());
				for (ValueHolder value : valueHolders) {
					String argType = (value.getType() != null ? ClassUtils.getShortName(value.getType()) :
							(value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null"));
					argTypes.add(argType);
				}
			}
			String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"No matching factory method found: " +
					(mbd.getFactoryBeanName() != null ?
						"factory bean '" + mbd.getFactoryBeanName() + "'; " : "") +
					"factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. " +
					"Check that a method with the specified name " +
					(minNrOfArgs > 0 ? "and arguments " : "") +
					"exists and that it is " +
					(isStatic ? "static" : "non-static") + ".");
		}else if (void.class == factoryMethodToUse.getReturnType()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Invalid factory method '" + mbd.getFactoryMethodName() +
					"': needs to have a non-void return type!");
		}else if (ambiguousFactoryMethods != null) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Ambiguous factory method matches found in bean '" + beanName + "' " +
					"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
					ambiguousFactoryMethods);
		}

		if (explicitArgs == null && argsHolderToUse != null) {
			// 将解析的构造函数加入缓存
			argsHolderToUse.storeCache(mbd, factoryMethodToUse);
		}
	}

	try {
		Object beanInstance;

		if (System.getSecurityManager() != null) {
			final Object fb = factoryBean;
			final Method factoryMethod = factoryMethodToUse;
			final Object[] args = argsToUse;
			beanInstance = AccessController.doPrivileged((PrivilegedAction<Object>) () ->
					beanFactory.getInstantiationStrategy().instantiate(mbd, beanName, beanFactory, fb, factoryMethod, args),
					beanFactory.getAccessControlContext());
		}else {
			beanInstance = this.beanFactory.getInstantiationStrategy().instantiate(
					mbd, beanName, this.beanFactory, factoryBean, factoryMethodToUse, argsToUse);
		}
		// 创建 Bean 对象，并设置到 bw(BeanWrapperImpl) 中
		bw.setBeanInstance(beanInstance);
		return bw;
	}
	catch (Throwable ex) {
		throw new BeanCreationException(mbd.getResourceDescription(), beanName,
				"Bean instantiation via factory method failed", ex);
	}
}
```

&nbsp;&nbsp;  流程如下

* `<1>`处，**确定工厂对象**。通过工厂方法名确认。见 [「1.2.1.1 确定工厂对象」](#1.2.1.1) 
   *  若**工厂方法名不为空**，则调用 `AbstractAutowireCapableBeanFactory#getBean(String name)` 方法，获取工厂对象 
   *  若**工厂方法名为空**，则可能为一个**静态工厂**，对于**静态工厂则必须提供工厂类的全类名**，同时设置 `factoryBean = null` 
* `<2>`处，**构造参数确认**。主要分三种情况。见 [「1.2.1.2 构造参数确认」](#1.2.1.2)
   * `explicitArgs` 参数。 这个参数是调用 `#getBean(...)` 方法时传递进来的 。见 [「1.2.1.2.1 explicitArgs 参数」](#1.2.1.2.1)
   * **缓存**中获取。见 [「1.2.1.2.2 缓存中获取」](#1.2.1.2.2)
   * **配置文件**中解析。见 [「1.2.1.2.3 配置文件中解析」](#1.2.1.2.3)
* `<3>`处，**构造函数确认**见 [「1.2.1.3 构造函数」](#1.2.1.3)
* `<4>`处，**创建 `Bean` 实例**。 利用 `Java` 反射执行工厂方法。见 [「1.2.1.4 创建 bean 实例」](#1.2.1.4)

>  **一句话概括**：确定工厂对象，然后获取构造函数和构造参数，最后调用 `InstantiationStrategy` 对象的 `#instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner, Constructor ctor, Object... args)` 方法，**来创建 Bean 实例** 

<span id = "1.2.1.1"></span>
#### 1.2.1.1 确定工厂对象

> 对应`<1>`处

&nbsp;&nbsp;  首先调用`RootBeanDefinition#getFactoryBeanName()`**获取工厂方法名** ,获取之后做如下处理

*  若**工厂方法名不为空**，则调用 `AbstractAutowireCapableBeanFactory#getBean(String name)` 方法，获取**工厂对象** 
*  若**工厂方法名为空**，则可能为一个**静态工厂**，对于**静态工厂则必须提供工厂类的全类名**，同时设置 `factoryBean = null` 

<span id = "1.2.1.2"></span>
#### 1.2.1.2 构造参数确认

> 对应`<2>`处

&nbsp;&nbsp; 工厂对象确定后，则是**确认构造参数**。构造参数的确认主要分为**三种**情况 

*  `explicitArgs` 参数 
*  **缓存**中获取 
*  **配置文件**中解析 

<span id = "1.2.1.2.1"></span>
##### 1.2.1.2.1 explicitArgs 参数

> 对应`<2.1>`处

&nbsp;&nbsp; `explicitArgs` 参数，是我们调用 `#getBean(...)` 方法时传递进来的。一般该参数，该参数就是用于初始化 `Bean` 时所传递的参数。如果该参数不为空，则可以确定构造函数的参数就是它了 

<span id = "1.2.1.2.2"></span>
##### 1.2.1.2.2 缓存中获取

> 对应`<2.2>`处

&nbsp;&nbsp; 在方法的最后，我们会发现这样一段 `argsHolderToUse.storeCache(mbd, factoryMethodToUse)` 代码。这段代码主要是将**构造函数、构造参数保存到缓存**中 

```java
// org.springframework.beans.factory.support.ConstructorResolver.java

public void storeCache(RootBeanDefinition mbd, Executable constructorOrFactoryMethod) {
	synchronized (mbd.constructorArgumentLock) {
		mbd.resolvedConstructorOrFactoryMethod = constructorOrFactoryMethod;
		mbd.constructorArgumentsResolved = true;
		if (this.resolveNecessary) {
			mbd.preparedConstructorArguments = this.preparedArguments;
		}
		else {
			mbd.resolvedConstructorArguments = this.arguments;
		}
	}
}


// org.springframework.beans.factory.support.RootBeanDefinition.java

/** 构造函数的缓存锁 */
final Object constructorArgumentLock = new Object();

/** 缓存已经解析的构造函数或者工厂方法 */
@Nullable
Executable resolvedConstructorOrFactoryMethod;

/** 标记字段，标记构造函数、参数已经解析了。默认为 `false`  */
boolean constructorArgumentsResolved = false;

/** 缓存已经解析的构造函数参数，包可见字段 */
@Nullable
Object[] resolvedConstructorArguments;

/** 缓存尚未完全解析的构造函数参数 */
@Nullable
Object[] preparedConstructorArguments;
```

&nbsp;&nbsp;  这里涉及到的几个参数，都是跟构造函数、构造函数缓存有关的 

*  `constructorArgumentLock` ：**构造函数的缓存锁** 
*  `resolvedConstructorOrFactoryMethod` ：**缓存已经解析的构造函数或者工厂方法** 
*  `constructorArgumentsResolved` ：**标记字段，标记构造函数、参数已经解析了**。默认为 `false` 
*  `resolvedConstructorArguments` ：**缓存已经解析的构造函数参数，包可见字段** 
*  `preparedConstructorArguments`：**缓存尚未完全解析的构造函数参数**

 &nbsp;&nbsp; 所以，**从缓存中获取就是提取这几个参数的值** 

```java
// org.springframework.beans.factory.support.ConstructorResolver.java

// 没有指定，则尝试从配置文件中解析
Object[] argsToResolve = null;
// <2.2> 首先尝试从缓存中获取
synchronized (mbd.constructorArgumentLock) {
	// 获取缓存中的构造函数或者工厂方法
	factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;
	if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
		// 获取缓存中的构造参数
		argsToUse = mbd.resolvedConstructorArguments;
		if (argsToUse == null) {
			// 获取缓存中的构造函数参数的包可见字段
			argsToResolve = mbd.preparedConstructorArguments;
		}
	}
}
// 缓存中存在,则解析存储在 BeanDefinition 中的参数
// 如给定方法的构造函数 A(int ,int )，则通过此方法后就会把配置文件中的("1","1")转换为 (1,1)
// 缓存中的值可能是原始值也有可能是最终值
if (argsToResolve != null) {
	argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve);
}
```

*  如果**缓存中存在构造参数**，则需要调用 `#resolvePreparedArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw, Executable executable, Object[] argsToResolve, boolean fallback)` 方法，进行转换 
*  因为**缓存中的值有可能是最终值，也有可能不是最终值**。比如我们构造函数中的类型为 `Integer` 类型的 1 ，但是原始的参数类型有可能是 `String` 类型的 `"1"` ，所以即便是从缓存中得到了构造参数，也**需要经过一番的类型转换确保参数类型完全对应** 

<span id = "1.2.1.2.3"></span>
##### 1.2.1.2.3 配置文件中解析

> 对应`<2.3>`处

&nbsp;&nbsp; 即没有通过**传递参数**的方式传递构造参数，**缓存**中也没有，那就只能通过**解析配置文件**获取构造参数了。

&nbsp;&nbsp; 在 `bean` 解析中我们了解到，配置文件中的信息都会转换到 `BeanDefinition` 实例对象中，所以配置文件中的参数可以直接通过 `BeanDefinition` 对象获取

```java
// org.springframework.beans.factory.support.ConstructorResolver.java

// <2.3> getBean() 没有传递参数，则需要解析保存在 BeanDefinition 构造函数中指定的参数
if (mbd.hasConstructorArgumentValues()) {
	// <2.3.1> 构造函数的参数
	ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
	resolvedValues = new ConstructorArgumentValues();
	// <2.3.2> 解析构造函数的参数
	// 将该 bean 的构造函数参数解析为 resolvedValues 对象，其中会涉及到其他 bean
	minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
}else {
	minNrOfArgs = 0;
}
```

*  `<2.3.1>` ，通过 `BeanDefinition` 的 `#getConstructorArgumentValues()` 方法，就可以获取构造信息了 
*  `<2.3.2>` ，有了构造信息就可以获取相关的参数值信息了，获取的参数信息包括直接值和引用，这一步骤的处理交由 `#resolveConstructorArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw, ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues)` 方法来完成。该方法会将构造参数信息解析为 `resolvedValues` 对象 并返回解析到的参数个数 `minNrOfArgs` 

<span id = "1.2.1.3"></span>
#### 1.2.1.3 构造函数确认

> 对应`<3>`处

&nbsp;&nbsp;  确定构造参数后，下一步则是确定构造函数 

*  第一步，是通过 `#getCandidateMethods()` 方法，获取所有的构造方法，同时对构造方法进行刷选 

*  然后，在对其进行排序处理（`AutowireUtils.sortFactoryMethods(candidates)`）。排序的主要目的，是为了能够**更加方便的**找到匹配的构造函数，因为构造函数的确认是根据参数个数确认的。排序的规则是：先按照 `public` / 非 `public` 构造函数**升序**，再按照构造参数数量**降序** 

&nbsp;&nbsp;  通过迭代 `candidates`（包含了所有要匹配的构造函数）的方式，依次比较其参数

*  如果显示提供了参数（`explicitArgs != null`），则直接比较两者**长度**是否相等，如果相等则表示找到了，否则继续比较 
*  如果没有显示提供参数，则需要获取 `org.springframework.core.ParameterNameDiscoverer` 对象。该对象为参数名称探测器，主要用于发现方法和构造函数的参数名称 

&nbsp;&nbsp;  将参数包装成 `ConstructorResolver.ArgumentsHolder` 对象。该对象用于保存参数，我们称之为参数持有者。当将对象包装成 `ArgumentsHolder` 对象后，我们就可以通过它来进行构造函数**匹配**。匹配分为**严格模式和宽松模式** 

*  **严格模式**：解析构造函数时，必须所有参数都需要匹配，否则抛出异常 
*  **宽松模式**：使用具有”最接近的模式”进行匹配 

 &nbsp;&nbsp; 判断的依据是根据 `BeanDefinition` 的 `isLenientConstructorResolution` 属性（该参数是我们在构造 `AbstractBeanDefinition` 对象是传递的）来获取类型差异权重（`typeDiffWeight`） 的 

*  如果 `typeDiffWeight < minTypeDiffWeight` ，则代表“最接近的模式”，选择其作为构造函数 
*  否则，只有两者具有相同的参数数量，且类型差异权重相等才会纳入考虑范围 

<span id = "1.2.1.4"></span>

#### 1.2.1.4 创建 bean 实例

> 对应`<4>`处

 &nbsp;&nbsp; 工厂对象、构造函数、构造参数都已经确认了，则最后一步就是调用 `org.springframework.beans.factory.support.InstantiationStrategy` 对象的 `#instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner, Object factoryBean, final Method factoryMethod, @Nullable Object... args)` 方法，来创建 `bean` 实例 

```java
// org.springframework.beans.factory.support.SimpleInstantiationStrategy.java

/**
 * 线程变量，正在创建 Bean 的 Method 对象
 */
private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<>();

@Override
public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
		@Nullable Object factoryBean, final Method factoryMethod, @Nullable Object... args) {

	try {
		// 设置 Method 可访问
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(factoryMethod);
				return null;
			});
		}else {
			ReflectionUtils.makeAccessible(factoryMethod);
		}

		// 获得原 Method 对象
		Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();
		try {
			// 设置新的 Method 对象，到 currentlyInvokedFactoryMethod 中
			currentlyInvokedFactoryMethod.set(factoryMethod);
			// <x> 创建 Bean 对象
			Object result = factoryMethod.invoke(factoryBean, args);
			// 未创建，则创建 NullBean 对象
			if (result == null) {
				result = new NullBean();
			}
			return result;
		}
		finally {
			// 设置老的 Method 对象，到 currentlyInvokedFactoryMethod 中
			if (priorInvokedFactoryMethod != null) {
				currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
			}else {
				currentlyInvokedFactoryMethod.remove();
			}
		}
	}
	catch (IllegalArgumentException ex) {
		throw new BeanInstantiationException(factoryMethod,
				"Illegal arguments to factory method '" + factoryMethod.getName() + "'; " +
				"args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
	}
	catch (IllegalAccessException ex) {
		throw new BeanInstantiationException(factoryMethod,
				"Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
	}
	catch (InvocationTargetException ex) {
		String msg = "Factory method '" + factoryMethod.getName() + "' threw exception";
		if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory &&
				((ConfigurableBeanFactory) owner).isCurrentlyInCreation(bd.getFactoryBeanName())) {
			msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " +
					"declaring the factory method as static for independence from its containing instance. " + msg;
		}
		throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
	}
}
```

 &nbsp;&nbsp; 核心的部分，在于 `<x>` 处，**利用 `Java` 反射执行工厂方法并返回创建好的实例** 

```java
// org.springframework.beans.factory.support.SimpleInstantiationStrategy.java

// <x> 创建 Bean 对象
Object result = factoryMethod.invoke(factoryBean, args);
```

