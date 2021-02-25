package fan.zhuyi.selfish.language.node;

import com.oracle.truffle.api.dsl.TypeSystem;

import java.math.BigDecimal;
import java.math.BigInteger;

@TypeSystem({long.class, double.class, String.class, BigInteger.class, BigDecimal.class})
public class SelfishTypes {

}
