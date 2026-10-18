package dev.anvilcraft.pullwires.power.link;

/**
 * {@link PrioritySliderMath} 的纯逻辑自检。
 *
 * <p>钉死三件事：滑条两端正好映射到 0 / 15、中间的整数可以原样往返（拖到哪就是哪），
 * 以及「松手才提交」用的增量在越界输入下仍先夹取。</p>
 */
public final class PrioritySliderSelfCheck {
    private static int assertions;
    private static int failures;

    private PrioritySliderSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        failures = 0;

        expect("滑条最左端就是优先级 0", PrioritySliderMath.proportionOf(PolePriority.MIN) == 0.0D);
        expect("滑条最右端就是优先级 15", PrioritySliderMath.proportionOf(PolePriority.MAX) == 1.0D);
        expect("默认优先级 7 落在滑条中段", Math.abs(PrioritySliderMath.proportionOf(7) - 7.0D / 15.0D) < 1.0E-9D);

        expect("比例 0 映射回优先级 0", PrioritySliderMath.priorityAt(0.0D) == 0);
        expect("比例 1 映射回优先级 15", PrioritySliderMath.priorityAt(1.0D) == 15);
        expect("正中间四舍五入到 8", PrioritySliderMath.priorityAt(0.5D) == 8);
        expect("比例小于 0 先夹到 0", PrioritySliderMath.priorityAt(-0.3D) == 0);
        expect("比例大于 1 先夹到 15", PrioritySliderMath.priorityAt(1.3D) == 15);

        boolean roundTrips = true;
        for (int value = PolePriority.MIN; value <= PolePriority.MAX; value++) {
            roundTrips &= PrioritySliderMath.priorityAt(PrioritySliderMath.proportionOf(value)) == value;
        }
        expect("0–15 每个整数都能原样往返（拖到哪就是哪）", roundTrips);

        expect("上调增量为正", PrioritySliderMath.deltaFor(10, 7) == 3);
        expect("下调增量为负", PrioritySliderMath.deltaFor(3, 7) == -4);
        expect("目标越界时按 0 夹取", PrioritySliderMath.deltaFor(-5, 7) == -7);
        expect("目标越界时按 15 夹取", PrioritySliderMath.deltaFor(99, 7) == 8);
        expect("镜像本身就越界时也先夹取", PrioritySliderMath.deltaFor(15, 99) == 0);

        if (failures == 0) {
            System.out.println("[pullwires] PrioritySlider ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] PrioritySlider FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    private static void expect(String name, boolean condition) {
        assertions++;
        if (condition) return;
        failures++;
        System.out.println("[pullwires] FAIL " + name);
    }
}
