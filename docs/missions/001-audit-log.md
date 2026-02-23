# Миссия 001: Реализация Аудита

1. **Сущности**: Создать AuditRecordEntity в пакете `ru.petrov.gateway.model`.
2. **Сервис**: Создать AuditService в `ru.petrov.gateway.service`.
3. **Транзакции**: Метод log ОБЯЗАН иметь `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
4. **Интеграция**: Внедрить в TaskServiceImpl (метод createAndDispatch).
5. **Точки**: RECEIVED (старт), CACHE_HIT (при дедупликации), SENT_TO_RABBIT (перед отправкой в очередь).
6. **Запрет**: Не трогать логику PESSIMISTIC_WRITE и расчет SHA-256.
