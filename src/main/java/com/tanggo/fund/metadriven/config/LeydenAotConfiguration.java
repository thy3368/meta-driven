package com.tanggo.fund.metadriven.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Project Leyden AOT 配置
 * <p>
 * 为 AOT (Ahead-of-Time) 编译和原生镜像构建提供 Runtime Hints。
 * 这些 hints 帮助 Spring Boot 4 + GraalVM 正确处理反射、资源和代理。
 * <p>
 * 符合 CLAUDE.md 低时延开发标准，优化启动时间和运行时性能。
 *
 * @author Claude Code
 * @since 1.0.0
 */
@Configuration
@ImportRuntimeHints(LeydenAotConfiguration.MetaDrivenRuntimeHints.class)
public class LeydenAotConfiguration {

    /**
     * Runtime Hints 注册器
     * <p>
     * 为脚本引擎、动态类加载和反射操作提供 hints。
     */
    static class MetaDrivenRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // 1. 注册反射 hints - 用于动态元数据处理
            registerReflectionHints(hints);

            // 2. 注册资源 hints - 用于配置文件和模板加载
            registerResourceHints(hints);

            // 3. 注册代理 hints - 用于 AOP 和事务代理
            registerProxyHints(hints);

            // 4. 注册序列化 hints - 用于 JSON/XML 处理
            registerSerializationHints(hints);

            // 5. 注册 JNI hints - 用于本地库调用（如果需要）
            registerJniHints(hints);
        }

        /**
         * 注册反射 hints
         * <p>
         * 对于动态加载的类和脚本引擎，需要显式声明反射访问权限。
         */
        private void registerReflectionHints(RuntimeHints hints) {
            // Groovy 脚本引擎支持
            try {
                // 注册 Groovy 相关类
                hints.reflection()
                        .registerType(
                                Class.forName("groovy.lang.GroovyShell"),
                                builder -> builder
                                        .withMembers(
                                                org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                                org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
                                        )
                        );

                hints.reflection()
                        .registerType(
                                Class.forName("groovy.lang.Script"),
                                builder -> builder
                                        .withMembers(
                                                org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                                org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS,
                                                org.springframework.aot.hint.MemberCategory.DECLARED_FIELDS
                                        )
                        );
            } catch (ClassNotFoundException e) {
                // Groovy 不在 classpath 中，跳过
            }

            // Janino 编译器支持
            try {
                hints.reflection()
                        .registerType(
                                Class.forName("org.codehaus.janino.ScriptEvaluator"),
                                builder -> builder
                                        .withMembers(
                                                org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                                org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
                                        )
                        );
            } catch (ClassNotFoundException e) {
                // Janino 不在 classpath 中，跳过
            }
        }

        /**
         * 注册资源 hints
         * <p>
         * 声明需要在原生镜像中包含的资源文件。
         */
        private void registerResourceHints(RuntimeHints hints) {
            // 注册 application.properties 和 YAML 配置文件
            hints.resources()
                    .registerPattern("application*.properties")
                    .registerPattern("application*.yml")
                    .registerPattern("application*.yaml");

            // 注册元数据相关资源
            hints.resources()
                    .registerPattern("META-INF/spring.factories")
                    .registerPattern("META-INF/spring/*.factories")
                    .registerPattern("META-INF/services/*");

            // 注册静态资源（如果有）
            hints.resources()
                    .registerPattern("static/**")
                    .registerPattern("templates/**");
        }

        /**
         * 注册代理 hints
         * <p>
         * 为 Spring AOP 和事务管理声明需要的代理类。
         */
        private void registerProxyHints(RuntimeHints hints) {
            // Spring AOP 代理
            // 如果使用了 @Transactional 或其他 AOP 注解，在这里注册接口

            // 示例：注册服务接口的代理
            // hints.proxies().registerJdkProxy(YourServiceInterface.class);
        }

        /**
         * 注册序列化 hints
         * <p>
         * 为 Jackson 和其他序列化框架提供 hints。
         */
        private void registerSerializationHints(RuntimeHints hints) {
            // Jackson 序列化支持
            // 如果有需要序列化的 DTO 或实体类，在这里注册

            // 示例：
            // hints.serialization().registerType(YourDto.class);
        }

        /**
         * 注册 JNI hints
         * <p>
         * 如果应用需要调用本地库（如性能优化的 C/C++ 库），在这里声明。
         */
        private void registerJniHints(RuntimeHints hints) {
            // 目前没有 JNI 需求，预留接口
            // hints.jni().registerType(NativeClass.class);
        }
    }
}
