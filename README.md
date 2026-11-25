<div align="center">

<img src="https://i.imgur.com/LvJD97T.png" alt="logo" style="width:25%">

# Ymir

<div align="center" >
  <img align="center" src="https://img.shields.io/badge/version-1.15.5 (dbd1a96)-blue">
  <img align="center" src="https://img.shields.io/badge/minecraft-forge 1.7.10-blue">
  <img align="center" src="https://img.shields.io/badge/maintainer-Zeldown-orange">
  <img align="center" src="https://img.shields.io/maintenance/yes/9999">
</div>

<br>

Créez, chargez, copiez et supprimez des mondes Minecraft pendant que le serveur tourne, en une ligne.
<br><br>
Ymir paie à votre place tous les pièges du multi-monde sur Forge et Bukkit : dimensions fantômes, fichiers de région jamais relâchés, identifiants de dimension qui se télescopent, mondes vides ignorés par les mods.

[Pourquoi](#pourquoi) • [Utilisation](#utilisation) • [Options](#options) • [Gradle](#gradle) • [Build](#build) • [Publish](#publish)

</div>

## Pourquoi

Créer un monde à chaud, c'est trois lignes. Le faire sans rien casser, beaucoup moins. Ymir règle ce que l'on découvre en production :

- **Plus de monde fantôme.** Une dimension déchargée est désinscrite. Sans ça, un joueur qui se reconnecte force le serveur à recréer sa dimension par le chemin vanilla, dans un dossier `DIM<id>` parallèle au vôtre, qui reste chargé et sauvegardé à vie.
- **Plus de conflit d'identifiants.** L'identifiant de dimension est relâché à chaque chargement, donc deux mondes ne peuvent jamais revendiquer le même numéro, quoi qu'il arrive au redémarrage.
- **Changer de serveur sans crash.** Derrière un proxy, le client garde l'identifiant de dimension d'un monde d'un autre serveur, et Forge ignore alors le provider que le nouveau serveur annonce pour ce même numéro : un monde du Nether reçu dans une dimension avec ciel fait planter le client au premier chunk. Côté client, Ymir réinscrit la dimension avec le provider annoncé ; le mod doit donc aussi être installé chez les joueurs.
- **Fichiers réellement relâchés.** Les fichiers de région restent ouverts après un déchargement : la suppression échoue sur NFS et un monde recréé au même endroit ressert les anciens chunks. Ymir vide la file d'écriture puis ferme et retire du cache les fichiers du monde.
- **Des centaines de mondes sans « Too many open files ».** Le serveur garde ouverts les fichiers de région de chaque monde chargé, quatre ou plus par monde : avec une limite de 1024 fichiers ouverts, un serveur de mondes individuels sature vers 150 joueurs, puis plus aucun monde, verrou de session ou sauvegarde ne s'ouvre. Les mondes Ymir partagent un seul cache de 128 fichiers de région ouverts au plus, à côté de celui du serveur : un fichier inutilisé depuis le plus longtemps est fermé pour en ouvrir un autre et rouvert à la demande, jamais pendant une lecture ou une écriture.
- **Suppression sans gel ni course.** Tout le travail sur le disque se fait en arrière-plan : vider la file d'écriture, fermer les fichiers de région, renommer le dossier dans une corbeille puis l'effacer. On peut recréer un monde du même nom immédiatement : sa préparation attend que l'ancien dossier ait quitté la place.
- **Un vide vraiment vide.** Le type `VOID` est un vrai type de monde Forge, pas un générateur Bukkit — ce dernier est ignoré dès qu'un mod fournit le provider du monde. Les chunks sont marqués décorés, donc aucun mod ne vient y semer de minerai.
- **Déchargement automatique** des mondes inactifs, et rien à faire à l'arrêt du serveur.
- **Aussi sans Bukkit.** Sur un serveur Forge seul, comme l'environnement de développement, Ymir crée, charge et téléporte lui-même, avec la même API et les mêmes dossiers sur le disque. Comme sous Bukkit, un monde ne se décharge jamais de lui-même : seul `unloadAfter`, `deleteAfter` ou un appel à `unload` le décharge.

## Utilisation

### Créer

```java
final YmirWorld world = Ymir.create(YmirWorldConfig.create("arene")
    .type(YmirWorldType.VOID)
    .platform(4)
    .gamerule("keepInventory", true)
    .unloadAfter(Duration.ofMinutes(5)));

world.teleport(player);
```

`create` construit le monde s'il n'existe pas et le charge sinon, avec la même configuration. Le monde se décharge seul après cinq minutes sans personne, et se recharge à la première téléportation. `teleport(player)` pose le joueur au spawn du monde, tourné vers l'orientation du spawn (`SpawnYaw` et `SpawnPitch` du level.dat sous Asgard, vers le sud sans Asgard).

### Retrouver

```java
Ymir.get("arene");          // par nom
Ymir.get(player);           // le monde Ymir où se trouve un joueur, s'il y en a un
Ymir.get(player.worldObj);  // idem à partir d'un monde Minecraft
Ymir.getWorlds();           // tout ce qu'Ymir connaît
Ymir.exists("arene");       // le dossier est-il sur le disque
```

### Copier, supprimer

```java
world.copy("arene-2").thenAccept(copy -> copy.teleport(player));
world.delete();
```

La copie se fait hors du thread serveur et vous rend la main dessus : la suite du `thenAccept` s'exécute là où vous pouvez toucher au jeu. La suppression exige un monde vide de joueurs, sinon elle lève une `YmirWorldException`.

Les deux existent aussi à partir d'une simple configuration, sans jamais charger le monde :

```java
Ymir.copy(YmirWorldConfig.create("arene"), YmirWorldConfig.create("arene-2"));
Ymir.delete(YmirWorldConfig.create("arene"));
```

Un monde chargé est déchargé proprement avant l'opération, un monde absent du serveur est traité directement sur le disque. C'est la forme à utiliser pour régénérer ou archiver un monde que personne n'occupe.

N'appelez jamais `.join()` sur ces futures depuis le thread serveur : elles se terminent dessus, vous attendriez votre propre tick. Enchaînez avec `thenAccept` ou `whenComplete`.

### Partir d'un modèle

```java
Ymir.createAsync(YmirWorldConfig.create("donjon-" + id)
    .template(new File(server, "templates/donjon"))
    .unloadAfter(Duration.ofMinutes(1)))
    .thenAccept(donjon -> donjon.teleport(player));
```

`createAsync` copie le modèle sur des threads dédiés, hors du thread serveur, puis crée le monde sur le thread serveur, autant de mondes par tick que le permet un budget de 12 ms : vingt joueurs qui arrivent ensemble entrent en quelques ticks sans geler le serveur.

### Mondes jetables

```java
Ymir.createAsync(YmirWorldConfig.create("donjon-" + id)
    .template(new File(server, "templates/donjon"))
    .temporary(true)
    .deleteAfter(Duration.ofSeconds(30))
    .preload(3))
    .thenAccept(donjon -> donjon.teleport(player));
```

Un monde `temporary` n'écrit jamais rien sur le disque et ses chunks se lisent directement dans les fichiers de région du modèle, sans les copier : cent mondes faits du même modèle partagent les mêmes fichiers ouverts. S'il reste sur le disque après un arrêt brutal, il est supprimé au démarrage suivant avec sa configuration WorldGuard. Tant qu'un joueur y est, les chunks qu'il quitte se déchargent quand même : un chunk jamais modifié (ni bloc, ni lumière, ni bloc-entité, aucune entité passée) est simplement oublié et se relit à l'identique depuis le modèle ou la carte source ; un chunk modifié est gardé jusqu'à la suppression du monde, sans être tické, et revient tel quel. Il est compressé hors du thread serveur (quelques Ko par chunk de surface) et, au-delà de 4 Mo de chunks compressés par monde, les plus anciens passent dans un fichier du dossier du monde, supprimé avec lui ; la relecture se fait au chargement du chunk, hors du thread serveur quand le chargement est asynchrone. Un minerai cassé ne réapparaît donc pas et un coffre vidé ne se remplit pas. Avec `volatileChunks(true)`, aucun chunk n'est gardé : un chunk quitté est toujours relu depuis le modèle, modifications perdues. C'est au mod de rejouer son état à chaque chargement (`ChunkEvent.Load`), ce qui évite de garder en mémoire, et de resérialiser à chaque déchargement, chaque chunk qu'un bloc posé ou une entité de passage a sali. `deleteAfter` le supprime quand il est resté vide ce temps : un joueur qui se reconnecte avant retrouve son monde. Le délai ne court pas tant que le préchargement n'est pas fini et il repart de zéro quand le monde est rendu, même si la file d'attente a duré plus longtemps que lui. Un monde supprimé ne se recharge plus : `load` et `teleport` lèvent une `YmirWorldException`, un nouveau monde du même nom se crée avec `Ymir.create`. Les suppressions dues sont faites deux par tick au plus. `preload` charge les chunks autour du spawn avant de rendre le monde, sans dépasser un budget de chunks par tick partagé par tous les mondes.

Les blocs du modèle gardent leur nature même si les identifiants numériques ont changé depuis sa création (mod ajouté ou retiré) : chaque chunk lu dans le modèle passe ses blocs par la table des noms de blocs du `level.dat` du modèle, convertie une fois par modèle vers les identifiants du serveur. Un bloc dont le mod n'est plus installé devient de l'air ; un modèle sans table, ou dont la table correspond déjà au serveur, est lu tel quel.

### Précharger

```java
world.preload(chunkX, chunkZ, 2).thenAccept(loaded -> loaded.teleport(player, x, y, z, yaw, pitch));
```

Les chunks demandés se chargent dans un budget de 5 ms et 64 chargements faits sur le thread serveur par tick, partagé par tous les mondes et monde après monde, dans l'ordre des demandes : le premier arrivé n'attend pas les suivants. Ils viennent depuis le disque en arrière-plan quand ils existent, et ces lectures ne consomment pas le budget : le joueur arrive dans une zone déjà chargée, sans gel du serveur. Dans un monde source, les chunks préchargés sont lus, décompressés et tournés en arrière-plan, et passent devant la file de génération du serveur.

### Lire une carte source

```java
Ymir.createAsync(YmirWorldConfig.create("terres-" + id)
    .source(YmirWorldSource.create(new File(server, "maps/terres"))
        .offset(120, -45)
        .rotation(YmirWorldRotation.random(random))
        .bounds(-312, -312, 311, 311))
    .deleteAfter(Duration.ofSeconds(10)))
    .thenAccept(terres -> terres.teleport(player));
```

Un monde source démarre vide et lit chaque chunk à la demande dans une carte déjà générée, ouverte en lecture seule et partagée par tous les mondes qui l'utilisent : rien n'est copié, une carte de plusieurs gigaoctets sert des centaines de mondes. Le chunk lu est décalé de `offset` chunks, tourné de `rotation` (un quart de tour vers la droite à chaque cran) avec ses blocs orientés, ses biomes, ses entités et ses blocs-entités, puis il vit comme un chunk normal : il est sauvegardé dans le monde et relu depuis le disque ensuite. Hors de `bounds` (en chunks, dans les coordonnées du monde) le monde reste vide. Les identifiants de blocs de la carte sont convertis comme ceux d'un modèle. Le monde n'est ni peuplé ni décoré par les mods, et il fonctionne pour tous les environnements, Nether compris. Comme à la génération vanilla, chaque chunk lu dans la carte reçoit les animaux de son biome (même densité qu'un monde généré), une seule fois par lecture depuis la carte et seulement quand il entre dans la zone simulée d'un joueur (la distance de vue du serveur autour de lui) : un chunk lu pour la vue lointaine reste sans animaux tant qu'aucun joueur ne s'en approche, et une carte nettoyée de ses entités retrouve sa faune là où l'on joue. Dans un monde `temporary` lu depuis une carte, un chunk dont les blocs n'ont pas changé est oublié au déchargement avec ses entités : relu depuis la carte, il reçoit de nouveaux animaux à sa prochaine entrée dans la zone simulée, et leur nombre reste celui d'une génération vanilla de la zone jouée ; un chunk modifié est gardé en mémoire avec ses entités et revient avec elles, sans nouvelle apparition.

La carte doit être carrée et centrée sur 0, 0, sinon des chunks manquent dans le monde. Placez-la sur un disque local rapide : chaque chunk est lu au hasard dans la carte. Un chunk demandé par `preload` est préparé en arrière-plan, et sur le thread serveur il ne reste qu'à poser ses tableaux de blocs, ses entités et ses blocs-entités. Quand un joueur change de chunk, l'anneau de chunks à la distance de vue + 1 autour de lui est lu de la même façon en avance : en se déplaçant, il n'entre que dans des chunks déjà préparés. Les chunks lus sont marqués comme éclairés (la lumière de la carte est copiée et tournée avec eux), et un monde source ne crée jamais de fichier de région vide sur le thread serveur.

Pour choisir un point d'apparition sans charger le monde, lisez directement la carte, déjà décalée et tournée :

```java
source.read(chunkX, chunkZ).thenAccept(chunk -> chunk.ifPresent(storages -> storages[y >> 4].getBlockByExtId(x & 15, y & 15, z & 15)));
```

La suite de `read` s'exécute hors du thread serveur : lisez les blocs, ne touchez pas au jeu. Les chunks lus ainsi (256 au plus par source) sont repris par le `preload` du monde créé avec cette source au lieu d'être relus, puis oubliés : ne modifiez pas les tableaux de blocs reçus. Les lectures d'un monde sans ciel (Nether) ne sont pas reprises.

L'orientation des blocs vanilla est fournie (troncs, escaliers, lianes, torches, coffres, portes…). Un mod enregistre celle de ses blocs :

```java
YmirBlockRotationRegistry.inst().register((metadata, rotation) -> metadata + rotation.getQuarters() & 3, MyBlocks.STATUE);
```

### Limiter le monde à sa zone utile

```java
world.addBounds(YmirWorldBox.ofBlocks(minX, minZ, maxX, maxZ).expand(1));
world.removeBounds(box);
```

Un monde Ymir peut se limiter à une zone faite d'une ou plusieurs boîtes de chunks : le serveur ne charge, ne génère, n'envoie et ne parcourt jamais un chunk hors de ces boîtes, quel que soit le rayon de vue, et les tickets de chargement, l'apparition des mobs et les entités n'y entrent pas (une entité qui y passe disparaît, un joueur est ramené à sa dernière position, `teleport` refuse une destination hors zone). Pour la lumière et le tick des entités, un chunk hors zone compte comme un chunk vide déjà chargé : le bord de la zone se comporte comme le reste du monde. Le rayon de vue peut donc monter sans charger le vide autour d'une grotte ou d'un labo.

La zone se calcule seule à la création : pour un monde fait d'un `template`, ce sont les chunks du modèle qui contiennent au moins un bloc, une entité ou un bloc-entité (les chunks vides des fichiers de région ne comptent pas), plus une marge d'un chunk ; le calcul se fait une fois par modèle, hors du thread serveur, et se garde tant que les fichiers de région du modèle ne changent pas. Pour un monde `source` borné, ce sont les `bounds` de la source plus une marge d'un chunk. Des boîtes passées par `bounds` à la création remplacent ce calcul. Le chunk du spawn et ses voisins sont toujours dans la zone. Dans un monde fait d'un `template`, un chunk de cette zone absent du modèle (la marge comprise) n'est jamais généré par le générateur du monde : il est vide, quel que soit le type du monde, et l'on ne voit autour du modèle que ce qu'il contient ; les boîtes ajoutées ensuite par `addBounds` gardent le générateur du monde. `addBounds` et `removeBounds` changent la zone d'un monde déjà créé (salle collée puis retirée) : les chunks d'une boîte ajoutée se chargent et s'envoient tout de suite, ceux d'une boîte retirée sont retirés des joueurs et déchargés. Un monde sans aucune boîte n'a pas de limite. Il faut Asgard avec les zones de chunks ; sinon le monde charge tout ce qui est en vue et un avertissement s'affiche une fois.

### Suivre le cycle de vie

```java
@SubscribeEvent
public void onCreate(final YmirWorldEvent.Create event) {
    event.getWorld().setGamerule("doDaylightCycle", false);
}
```

`Create`, `Load`, `Unload` et `Delete` sont postés sur le bus Forge, ainsi que `YmirPlayerEvent.Enter` et `YmirPlayerEvent.Leave` quand un joueur entre dans un monde Ymir ou en sort, connexion, déconnexion, mort et téléportation comprises.

## Options

Toutes se déclarent sur `YmirWorldConfig.create(nom)`.

| Option | Défaut | Rôle |
|---|---|---|
| `type` | `NORMAL` | `NORMAL`, `FLAT`, `AMPLIFIED`, `LARGE_BIOMES` ou `VOID` ; un type autre que `NORMAL` remplace celui d'un modèle |
| `seed` | aléatoire | graine, numérique ou textuelle |
| `structures` | `true` | génération des structures |
| `environment` | `NORMAL` | `NORMAL`, `NETHER` ou `THE_END` |
| `sky` | `true` | `false` crée un monde sans ciel, comme le Nether mais avec l'eau et la lave d'un monde normal : la lumière du ciel n'est ni calculée ni envoyée aux joueurs, seules les sources de lumière et une faible lumière d'ambiance éclairent. Sous Bukkit, il faut un serveur qui accepte les types de monde Forge (Cauldron) ; sinon le monde est créé avec un ciel et un avertissement s'affiche une fois |
| `spawn` | selon le type | point d'apparition imposé, sans la recherche vanilla (aussi évitée pour `VOID`, `FLAT` et une `source`) |
| `platform` | aucune | plateforme carrée de ce rayon sous le spawn, utile pour un monde vide |
| `gamerule` | aucune | gamerules appliquées à chaque chargement |
| `unloadAfter` | jamais | déchargement après ce temps sans joueur |
| `deleteAfter` | jamais | suppression après ce temps sans joueur |
| `temporary` | `false` | monde jetable : rien n'est écrit sur le disque, les chunks non modifiés se déchargent sans sauvegarde et les chunks modifiés restent en mémoire ; supprimé au démarrage s'il est resté sur le disque |
| `preload` | aucun | rayon de chunks chargés autour du spawn avant que `createAsync` rende le monde |
| `time` | cycle normal | heure figée, réappliquée à chaque chargement |
| `weather` | `true` | `false` garde un temps clair |
| `volatileChunks` | `false` | monde `temporary` seulement : un chunk déchargé n'est jamais gardé, il se relit toujours depuis le modèle ; le mod rejoue ses propres changements au chargement du chunk (`ChunkEvent.Load`) |
| `randomTicks` | `true` | `false` coupe les ticks aléatoires des blocs (pousse des plantes, propagation de l'herbe, chute des feuilles, fonte de la glace…) : les chunks du monde ne sont plus parcourus bloc par bloc à chaque tick, les ticks programmés (machines, liquides, redstone) continuent |
| `keepLoaded` | `false` | garde chargés tous les chunks déjà chargés d'un monde `temporary` ou `batchSave` tant qu'il existe, pour que les blocs-entités et les machines d'un petit monde tickent partout (labo) ; sans effet sur un monde ordinaire, déchargé par le serveur |
| `keepSpawnInMemory` | `false` | garde les chunks du spawn chargés (Bukkit seulement) ; à `false`, Bukkit ne prépare pas la zone du spawn à la création et les chunks proches du spawn se déchargent comme les autres hors de la vue, sauf dans un monde `keepLoaded` (Asgard ≥ `353a20ea`) |
| `autoSave` | `true` | sauvegarde automatique |
| `batchSave` | `false` | sort le monde de la sauvegarde automatique du serveur, qui écrit tous les chunks de tous les mondes dans le même tick : Ymir le sauvegarde toutes les 30 s par lots de 24 chunks par tick, un monde à la fois, et les chunks déchargés restent sauvegardés ; un monde vide qui attend sa suppression (`deleteAfter`) garde ses chunks en mémoire sans les sauvegarder ni les décharger, pour qu'une vague de déconnexions ne lance pas une salve d'écritures |
| `template` | aucun | dossier copié au lieu de générer le monde |
| `source` | aucune | carte lue à la demande, décalée, tournée et bornée, au lieu de générer le monde |
| `bounds` | calculée | boîtes de chunks (`YmirWorldBox`) hors desquelles rien n'est chargé ni envoyé ; sans elles, la zone vient du modèle ou de la source |
| `link` | aucun | dossier qui suit le monde, copié et supprimé avec lui, résolu depuis son nom |
| `worldGuard` | `false` | copie la configuration WorldGuard du monde principal et charge les régions du nouveau monde |
| `chat` | aucun | chat propre au monde et accès aux chats serveur et global, appliqués par PalaChats |
| `tab` | `SERVER` | joueurs affichés dans le tab, appliqués par le plugin Tab |

Le chat et le tab se décrivent dans Ymir et sont appliqués par les mods qui les affichent :

```java
YmirWorldConfig.create("donjon-" + id)
    .chat(YmirWorldChat.create()
        .name("dungeon")
        .server(false)
        .prefix(YmirWorldChatPrefix.emote("dungeon")))
    .tab(YmirWorldTab.create(YmirWorldTabScope.WORLD));
```

| Option du chat | Défaut | Rôle |
|---|---|---|
| `name` | aucun | nom de l'onglet du chat du monde, réservé aux joueurs présents dans le monde et sélectionné à l'entrée ; sans nom, pas de chat de monde |
| `server` | `true` | accès au chat serveur ; `false` le coupe dans les deux sens |
| `global` | `true` | accès au chat global ; `false` le coupe dans les deux sens |
| `prefix` | celui du serveur | `none()`, `emote(clé)` de la configuration d'emotes ou `url(lien)` d'une image, devant les messages envoyés depuis le monde |

| Option du tab | Défaut | Rôle |
|---|---|---|
| `create(portée)` | | `SERVER` tout le serveur, `WORLD` les joueurs du monde, `SELF` soi seul |
| `visible` | `true` | `false` retire les joueurs du monde du tab des joueurs de l'extérieur |

Un dossier lié se déclare par une fonction, pour qu'Ymir sache le nommer quand vous copiez le monde :

```java
YmirWorldConfig.create("arene").link(name -> new File(Ymir.getContainer(), "data/" + name));
```

## Gradle

To include Ymir in your project using Gradle, add the following dependency to your `build.gradle` file:

```gradle
repositories {
    maven {
        name = "forge"
        url = "http://repository.palagitium.dev/artifactory/Paladium-Dev"
        credentials {
            username = getRepositoryUser()
            password = getRepositoryPassword()
        }
    }
}

def getRepositoryUser() {
    if(System.getenv('MAVEN_REPO_USER') != null) {
        return System.getenv('MAVEN_REPO_USER')
    }else {
        return project.findProperty('MAVEN_REPO_USER')
    }

    return ""
}

def getRepositoryPassword() {
    if(System.getenv('MAVEN_REPO_PASS') != null) {
        return System.getenv('MAVEN_REPO_PASS')
    }else {
        return project.findProperty('MAVEN_REPO_PASS')
    }

    return ""
}

configurations.all {
   resolutionStrategy {
   	cacheDynamicVersionsFor 60, 'seconds'
   }
}

dependencies {
    compile "fr.paladium:ymir:+:dev"
}
```

Déclarez ensuite la dépendance dans votre mod :

```java
@Mod(modid = Constants.MOD_ID, version = Constants.VERSION, dependencies = "required-after:ymir")
```

## Build

```sh
gradlew build         # Build le projet dans le dossier Ymir/build/libs.
```

## Publish

```sh
gradlew publish       # Met en ligne le projet sur repository.palagitium.dev
```
