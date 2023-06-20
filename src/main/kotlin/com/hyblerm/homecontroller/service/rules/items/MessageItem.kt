package com.hyblerm.homecontroller.service.rules.items

import com.hyblerm.homecontroller.service.repository.DataAccess

class MessageItem(dataAccess: DataAccess) : GenericItem("push_message", dataAccess)
