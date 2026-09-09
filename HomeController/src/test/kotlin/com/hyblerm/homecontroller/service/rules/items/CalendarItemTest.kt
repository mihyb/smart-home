package com.hyblerm.homecontroller.service.rules.items

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.items.CalendarItem
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

private const val ACTIVE_STATUS =
    "{\"1\":{\"key\":\"17\",\"value\":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]},\"99\":\"OFF,ON\",\"100\":{\"event\":false,\"lastItemState\":-1,\"inactive\":false}}"
private const val INACTIVE_STATUS =
    "{\"1\":{\"key\":\"17\",\"value\":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]},\"99\":\"OFF,ON\",\"100\":{\"event\":false,\"lastItemState\":-1,\"inactive\":true}}"

class CalendarItemTest {

    private val dataAccess = Mockito.mock(DataAccess::class.java)
    private var calendar = CalendarItem("item", dataAccess)

    @Test
    fun `is active should return active if status contains active`() {
        whenever(dataAccess.getItem(calendar.id)).thenReturn(OpenHabModel.Item("link", calendar.id, ACTIVE_STATUS))

        Assertions.assertThat(calendar.isActive()).isTrue
    }

    @Test
    fun `is active should return inactive if status doesn't contain active`() {
        whenever(dataAccess.getItem(calendar.id)).thenReturn(OpenHabModel.Item("link", calendar.id, INACTIVE_STATUS))

        Assertions.assertThat(calendar.isActive()).isFalse
    }
}
