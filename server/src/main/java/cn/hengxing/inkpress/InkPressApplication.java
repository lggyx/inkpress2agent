package cn.hengxing.inkpress;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.hengxing.inkpress.modules.**.mapper")
public class InkPressApplication {

    public static void main(String[] args) {
        SpringApplication.run(InkPressApplication.class, args);
    }
}
