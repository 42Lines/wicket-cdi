package net.ftlines.wicket.cdi;

import javax.enterprise.inject.spi.BeanManager;

import org.apache.wicket.util.lang.Args;

/**
 * Default implementation of {@link INonContextualManager} using {@link NonContextual} helper
 * 
 * @author igor
 * 
 */
class NonContextualManager implements INonContextualManager
{
	private final BeanManager beanManager;

	/**
	 * Constructor
	 * 
	 * @param beanManager
	 */
	public NonContextualManager(BeanManager beanManager)
	{
		Args.notNull(beanManager, "beanManager");

		this.beanManager = beanManager;
	}

	@Override
	public <T> void postConstruct(T instance)
	{
		Args.notNull(instance, "instance");
		NonContextual.of(instance.getClass(), beanManager).postConstruct(instance);
	}

	@Override
	public <T> void preDestroy(T instance)
	{
		Args.notNull(instance, "instance");
		NonContextual.of(instance.getClass(), beanManager).preDestroy(instance);
	}

}
