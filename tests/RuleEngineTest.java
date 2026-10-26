package io.shakeguard.app;
import java.util.List;
public final class RuleEngineTest {
    private static int assertions;
    private static final List<RuleEngine.Rule> RULES=List.of(new RuleEngine.Rule("news.app","shop.app",6,true));
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        RuleEngine e=new RuleEngine();
        check(e.window("shop.app",100,RULES)==null,"No known source");
        e.reset();e.window("news.app",100,RULES);
        check(e.window("shop.app",500,RULES)!=null,"Direct transition matches");
        e.window("news.app",600,RULES);
        check(e.window("shop.app",800,RULES)==null,"Cooldown suppresses repeat");
        e.reset();e.window("news.app",100,RULES);e.click("news.app",200);
        check(e.window("shop.app",400,RULES)==null,"Recent click is allowed");
        e.reset();e.window("news.app",100,RULES);e.click("news.app",200);
        check(e.window("shop.app",1800,RULES)!=null,"Old click expires");
        e.reset();e.window("news.app",100,RULES);e.window("news.app",5000,RULES);
        check(e.window("shop.app",6200,RULES)==null,"Duplicate source cannot extend protection");
        e.reset();e.window("news.app",100,RULES);e.window("launcher.app",200,RULES);
        check(e.window("shop.app",300,RULES)==null,"Intermediate app invalidates chain");
        e.reset();e.window("news.app",100,RULES);
        check(e.window("other.app",300,RULES)==null,"Unconfigured target ignored");
        e.reset();e.window("news.app",100,RULES);e.reset();
        check(e.window("shop.app",300,RULES)==null,"Reset discards pending source");
        e.reset();e.window("news.app",100,RULES);e.click("different.app",200);
        check(e.window("shop.app",300,RULES)!=null,"Foreign click does not replace source click");
        e.reset();e.window("news.app",100,RULES);
        check(e.window("shop.app",300,List.of())==null,"No rules no action");
        e.reset();e.window("news.app",100,RULES);
        RuleEngine.Match m=e.window("shop.app",300,List.of(new RuleEngine.Rule("news.app","shop.app",6,false)));
        check(m!=null&&!m.rule.automatic,"Observe mode remains observe");
        e.reset();e.window("news.app",100,RULES);
        check(e.window("shop.app",6100,RULES)!=null,"Inclusive protection boundary");
        e.reset();e.window("news.app",100,RULES);e.click("news.app",200);
        check(e.window("shop.app",1700,RULES)==null,"Inclusive click boundary");
        e.reset();e.window("news.app",100,RULES);e.pane("news.app");
        check(e.window("shop.app",300,RULES)!=null,"Same-app pane preserves source");
        e.reset();e.window("news.app",100,RULES);e.pane("news.app");
        check(e.window("shop.app",6200,RULES)==null,"Pane does not extend startup period");
        e.reset();e.window("news.app",100,RULES);e.pane("system.dialog");
        check(e.window("shop.app",300,RULES)==null,"Unknown pane invalidates source");
        // Simulated device has slept 2 hours: elapsed=7,300,000; uptime=100,000.
        check(EventTiming.fresh(99900,100000),"Fresh event after deep sleep uses uptime");
        check(!EventTiming.fresh(98000,100000),"Delayed event is rejected");
        check(!EventTiming.fresh(100001,100000),"Future event is rejected");
        check(!EventTiming.fresh(0,100000),"Unset event time is rejected");
        System.out.println("PASS: "+assertions+" regression assertions");
    }
}
