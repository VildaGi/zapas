package com.zapas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ListDetailsTest {

    @Test
    void emptyInputGivesDefaults() {
        ListDetails d = ListDetails.normalize(null, null, "  ");
        assertNull(d.quantity());
        assertNull(d.note());
        assertEquals("шт", d.unit());
    }

    @Test
    void trimsNoteAndKeepsValidUnit() {
        ListDetails d = ListDetails.normalize(new BigDecimal("1.5"), " кг ", " зелёные ");
        assertEquals(new BigDecimal("1.5"), d.quantity());
        assertEquals("кг", d.unit());
        assertEquals("зелёные", d.note());
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> ListDetails.normalize(BigDecimal.ZERO, "шт", null));
        assertThrows(IllegalArgumentException.class, () -> ListDetails.normalize(new BigDecimal("-1"), "шт", null));
        assertThrows(IllegalArgumentException.class, () -> ListDetails.normalize(null, "бочка", null));
        assertThrows(IllegalArgumentException.class, () -> ListDetails.normalize(null, "шт", "x".repeat(201)));
    }
}
