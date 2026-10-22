package example.kronyka.infrastructure.config

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Component
class StringToUuidConverter : Converter<String, Uuid> {
    override fun convert(source: String): Uuid = Uuid.parse(source)
}
