package volvet.checkadv_gateway.contrato.conta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /v1/contas/{contaId}/whatsapp/onboarding}: dados devolvidos pela
 * janela do Embedded Signup. O {@code code} é de uso único e só o Gateway o troca por token.
 */
public record OnboardingWhatsapp(
        @NotBlank @Size(max = 2048) String code,
        @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String wabaId,
        @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String phoneNumberId) {

    @Override
    public String toString() {
        return "OnboardingWhatsapp[code=***, wabaId=" + wabaId + ", phoneNumberId=" + phoneNumberId + "]";
    }
}
