# Komponente koje sistem pravi po imenu iz manifesta ne smeju da se preimenuju.
# Bez ovoga R8 ih preimenuje, sistem ih ne nadje, i „pristup obavestenjima" u
# podesavanjima ne ponudi aplikaciju uopste — bez ijedne greske u dnevniku.
-keep class studio.room211.most.MainActivity { *; }
-keep class studio.room211.most.Listener { *; }
