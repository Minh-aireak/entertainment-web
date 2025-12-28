package com.MyProject.post.post_service.configuration;

import org.jspecify.annotations.NonNull;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Factory này cho phép Quartz Jobs sử dụng @Autowired
 *
 * GIẢI THÍCH:
 * - Mặc định Quartz tự tạo Job instances, không qua Spring
 * - Nên không thể @Autowired trong Job
 * - Class này override createJobInstance() để Spring inject dependencies
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory
        implements ApplicationContextAware {

    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(final ApplicationContext context) {
        beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(final @NonNull TriggerFiredBundle bundle) throws Exception {
        final Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);  // Inject Spring beans vào job
        return job;
    }
}
