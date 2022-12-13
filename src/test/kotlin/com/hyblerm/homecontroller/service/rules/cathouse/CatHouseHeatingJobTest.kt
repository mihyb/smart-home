package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.repository.ElectricityRepository
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRates
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
internal class CatHouseHeatingJobTest {

    private val ratesMap: Map<Int, Double> = mapOf(
        Pair(1, 2.0),
        Pair(2, 2.0),
        Pair(3, 3.0),
        Pair(4, 4.0),
        Pair(5, 5.0),
        Pair(6, 5.0),
        Pair(7, 2.0),
        Pair(8, 1.0),
    )
    private val eRates: ElectricityRates = ElectricityRates(ratesMap)

    @MockBean
    lateinit var electricityRepository: ElectricityRepository
    @MockBean
    lateinit var dataAccess: DataAccess

    @Autowired
    lateinit var catHouseHeatingJob: CatHouseHeatingJob

    // @Test
    fun heatingTest() {
        // whenever(electricityRepository.findByDate(any(), any())).thenReturn(listOf(eRates))
        mockItem("Cat_heating_mode", "ON")
        mockItem("TASMOTASWITCH8_TEMP", "5")
        mockItem("Cat_MinTemp", "10")

        catHouseHeatingJob.run()
    }

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }
}
