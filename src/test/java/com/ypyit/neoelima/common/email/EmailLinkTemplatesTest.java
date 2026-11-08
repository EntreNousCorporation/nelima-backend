package com.ypyit.neoelima.common.email;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les e-mails à lien portent le lien qu'on leur passe.
 *
 * <p>Le gabarit de réinitialisation lisait {@code ${token}} quand le code pose {@code token_link} :
 * Thymeleaf rend une variable absente en chaîne vide, sans erreur, et l'e-mail partait avec un
 * bouton mort et un lien de repli vide. Seul un rendu le voit.
 */
class EmailLinkTemplatesTest extends AbstractIntegrationTest {

    private static final String LINK = "https://partenaire.nelima.ci/reinitialiser?username=a%40b.ci&token=abc123";

    @Autowired
    private TemplateEngine templateEngine;

    @ParameterizedTest
    @ValueSource(strings = {"reset-password_fr", "welcome-user_fr"})
    void theButtonAndTheFallbackCarryTheLink(String template) {
        Context context = new Context();
        context.setVariable(EmailConstants.TOKEN_LINK, LINK);

        String body = this.templateEngine.process(template, context);

        assertThat(body).contains("href=\"" + LINK.replace("&", "&amp;") + "\"");
        assertThat(body).as("le lien de repli, à copier si le bouton ne s'ouvre pas")
                .contains(">" + LINK + "<");
    }
}
