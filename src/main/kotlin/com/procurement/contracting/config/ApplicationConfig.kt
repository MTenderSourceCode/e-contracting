package com.procurement.contracting.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(DaoConfiguration::class, ObjectMapperConfig::class, ServiceConfig::class, WebConfig::class)
class ApplicationConfig