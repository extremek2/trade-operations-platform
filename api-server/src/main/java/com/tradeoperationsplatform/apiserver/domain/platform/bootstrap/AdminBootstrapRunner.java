package com.tradeoperationsplatform.apiserver.domain.platform.bootstrap;

import com.tradeoperationsplatform.apiserver.domain.platform.service.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.Console;
import java.util.Arrays;

@Component
@Profile("bootstrap-admin")
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminBootstrapService bootstrap;
    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("bootstrap-admin")) throw new IllegalStateException("--bootstrap-admin 명령으로만 실행할 수 있습니다.");
        Console console = System.console();
        if (console == null) throw new IllegalStateException("비밀번호 비노출 입력을 위해 대화형 터미널에서 실행해 주세요.");
        String email = console.readLine("관리자 이메일: ");
        String name = console.readLine("관리자 이름: ");
        char[] password = console.readPassword("비밀번호 (12자 이상): ");
        char[] confirmation = console.readPassword("비밀번호 확인: ");
        try {
            if (password == null || !Arrays.equals(password, confirmation)) throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            boolean created = bootstrap.createInitialAdmin(email, name, password);
            console.printf(created ? "초기 시스템관리자를 생성했습니다.%n" : "이미 설정된 관리자입니다. 변경하지 않았습니다.%n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
