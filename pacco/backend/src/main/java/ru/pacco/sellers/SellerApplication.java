package ru.pacco.sellers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Заявка итальянского продавца. Partita IVA и IBAN проверяются по формату. */
public record SellerApplication(
        @NotBlank String legalName,
        @NotBlank @Pattern(regexp = "IT\\d{11}", message = "формат IT + 11 цифр") String partitaIva,
        @NotBlank @Pattern(regexp = "IT\\d{2}[A-Z]\\d{10}[0-9A-Z]{12}", message = "итальянский IBAN из 27 символов") String iban,
        String region,
        @NotBlank @Email String contactEmail) {
}
