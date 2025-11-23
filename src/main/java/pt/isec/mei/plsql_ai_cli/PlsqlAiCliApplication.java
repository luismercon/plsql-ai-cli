package pt.isec.mei.plsql_ai_cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlsqlAiCliApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(PlsqlAiCliApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.run(args);
	}

}
