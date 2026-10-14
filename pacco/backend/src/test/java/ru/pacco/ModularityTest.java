package ru.pacco;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Модули обращаются друг к другу только через публичные типы корневых пакетов и без циклов. */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(PaccoApplication.class);

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
