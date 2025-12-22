package com.MyProject.post.post_service.configuration;

import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;

@Configuration
public class QuartzConfig {

    @Bean
    public JobFactory jobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    /**
     * SchedulerFactoryBean - Core của Quartz
     *
     * @param dataSource - DataSource để lưu jobs (MySQL)
     * @param jobFactory - Factory để tạo jobs với Spring injection
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            @Qualifier("dataSource") DataSource dataSource,  // Dùng MySQL datasource
            JobFactory jobFactory
    ) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        // Set JobFactory để có thể @Autowired trong Jobs
        factory.setJobFactory(jobFactory);

        // Set DataSource - Quartz sẽ lưu jobs vào MySQL
        factory.setDataSource(dataSource);

        // Tự động start khi Spring Boot khởi động
        factory.setAutoStartup(true);

        // Đợi jobs đang chạy hoàn thành trước khi shutdown
        factory.setWaitForJobsToCompleteOnShutdown(true);

        // Overwrite jobs nếu đã tồn tại (khi restart app)
        factory.setOverwriteExistingJobs(false);

        return factory;
    }
}
