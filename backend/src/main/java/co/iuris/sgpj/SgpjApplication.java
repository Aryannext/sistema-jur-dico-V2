package co.iuris.sgpj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Sin esto, el planificador de alertas no se ejecutaria nunca y el sistema
// dejaria de cumplir su unica promesa (RF-24, RN-30).
@EnableScheduling
public class SgpjApplication {

	public static void main(String[] args) {
		SpringApplication.run(SgpjApplication.class, args);
	}

}
