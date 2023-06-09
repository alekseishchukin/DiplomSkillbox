# Search-Engine
Данный проект реализует поисковый движок, предоставляющий пользователю специальный
API со следующими основными функциями: 
<li>предварительное индексирование сайтов;</li>
<li>выдача основных сведений по сайтам;</li>
<li>поиск ключевых слов в проиндексированных сайтах и предоставление их пользователю.</li>

## Стэк используемых технологий
Spring Framework, JPA, JSOUP, SQL, Morphology Library Lucene

## Веб-страница
<p>
В проект также входит веб-страница, которая позволяет управлять процессами, реализованными
в движке.
<p>
Страница содержит три вкладки.

### Вкладка DASHBOARD
![image](https://user-images.githubusercontent.com/19908367/207224036-ba81ae73-c3a1-4627-bef3-f277e5103146.png)
Эта вкладка открывается по умолчанию. На ней
отображается общая статистика по всем проиндексированным сайтам, а также
детальная статистика и статус по каждому из сайтов (статистика,
получаемая по запросу <i>/statistics</i>).

### Вкладка MANAGEMENT
![image](https://user-images.githubusercontent.com/19908367/207224264-dae30c37-c9a6-49da-a67a-272a34d72f16.png)
На этой вкладке находятся инструменты управления 
поисковым движком — запуск (запрос <i>/startIndexing</i>) 
и остановка (запрос <i>/stopIndexing</i>) полной индексации
(переиндексации), а также возможность добавить (обновить)
отдельную страницу по ссылке (запрос <i>/indexPage/{pagePath}</i>).

### Вкладка SEARCH
![image](https://user-images.githubusercontent.com/19908367/207224493-56a19681-73c6-41f4-a4ba-5c33aef87237.png)<p>
Эта вкладка предназначена для тестирования поискового
движка. На ней находится поле поиска и выпадающий список с
выбором сайта, по которому искать, а при нажатии на кнопку
<i>SEARCH</i> выводятся результаты поиска (по запросу /search).

## Файл настройки
<i>application.yaml</i>

### Раздел server
<p>
В этом разделе задаётся параметр <i>port</i> — порт, через который контроллеры 
приложения "слушают" веб-запросы.

### Раздел spring
<p>
Здесь задаются параметры СУБД, в которой приложение хранит 
данные конфигурации.

### Раздел config
Список сайтов для индексации.
