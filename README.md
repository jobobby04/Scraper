This is where I am going to keep my website scrapers, so far this includes E-hentai.org tags

Guide:
Go (here)[https://e-hentai.org/tools.php?act=taggroup] and copy all the html to the respecive files in src\main\kotlin\exh\ , this will be what the scraper uses to make the EHTags.kt. I could not figure out how to directly connect to the website because it requires a account to access the page.

Run the main function in src\main\kotlin\main\Main.kt with the exhTagListScraper function not commented out, it will be commented out by default.

The EHTags.kt file will be generated into the build folder, then your done.