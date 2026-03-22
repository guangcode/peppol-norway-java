package io.github.peppolnorway.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * peppol-norway-java Spring Boot 启动类。
 *
 * <p><b>启动前置要求：</b></p>
 * <ol>
 *   <li>安装 oxalis-ng 到本地 Maven 仓库（若使用 SNAPSHOT 版本）</li>
 *   <li>设置 OXALIS_HOME 环境变量，或在 application.yml 中配置 oxalis.home</li>
 *   <li>OXALIS_HOME 目录中需包含有效的 oxalis.conf 和 PEPPOL 证书 keystore</li>
 * </ol>
 *
 * <p>Oxalis 初始化约需 5~15 秒（证书加载 + CXF/AS4 栈初始化），属正常现象。</p>
 */
@SpringBootApplication
public class PeppolSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PeppolSenderApplication.class, args);
    }
}
