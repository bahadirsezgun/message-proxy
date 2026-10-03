package tr.com.tatmetal.proxy.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class YesNoEnumTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsIdObjectBooleanAndString() {
        assertEquals(YesNoEnum.YES, mapper.readValue("{\"id\":1}", YesNoEnum.class));
        assertEquals(YesNoEnum.NO, mapper.readValue("false", YesNoEnum.class));
        assertEquals(YesNoEnum.YES, mapper.readValue("\"true\"", YesNoEnum.class));
        assertEquals(YesNoEnum.NO, mapper.readValue("0", YesNoEnum.class));
    }

    @Test
    void getById() {
        assertEquals(YesNoEnum.YES, YesNoEnum.get(1));
        assertNull(YesNoEnum.get(9));
    }
}
