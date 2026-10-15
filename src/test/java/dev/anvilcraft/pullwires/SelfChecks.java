package dev.anvilcraft.pullwires;

import dev.anvilcraft.pullwires.power.link.CrossDimensionSelfCheck;
import dev.anvilcraft.pullwires.power.link.InterconnectSettlementSelfCheck;
import dev.anvilcraft.pullwires.power.link.LinkRuntimeSelfCheck;
import dev.anvilcraft.pullwires.power.link.ManualLinkRuleSelfCheck;
import dev.anvilcraft.pullwires.power.link.PoleRangeRuleSelfCheck;
import dev.anvilcraft.pullwires.power.link.PoleRuntimeSelfCheck;
import dev.anvilcraft.pullwires.power.link.PowerLinkRuleSelfCheck;

/**
 * 附属模组全部纯逻辑自检的统一入口，由 Gradle 任务 {@code verifyAddon} 运行。
 *
 * <p>刻意不用 JUnit：这些算法都不依赖游戏运行环境，用普通 {@code main} 即可，
 * 既不引入额外依赖，也不受模组开发环境的影响。</p>
 */
public final class SelfChecks {
    private SelfChecks() {
    }

    public static void main(String[] args) {
        int failures = 0;
        failures += PowerLinkRuleSelfCheck.run();
        failures += PoleRuntimeSelfCheck.run();
        failures += InterconnectSettlementSelfCheck.run();
        failures += ManualLinkRuleSelfCheck.run();
        failures += LinkRuntimeSelfCheck.run();
        failures += CrossDimensionSelfCheck.run();
        failures += PoleRangeRuleSelfCheck.run();

        if (failures > 0) {
            System.out.println("[pullwires] self-check FAILED: " + failures + " failed assertion(s)");
            System.exit(1);
        }
        System.out.println("[pullwires] all self-checks passed");
    }
}
